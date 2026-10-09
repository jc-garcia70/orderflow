package com.orderflow.inventory.application.service;

import com.orderflow.common.enums.StockRejectionReason;
import com.orderflow.common.event.inventory.FailedProductDto;
import com.orderflow.common.event.inventory.ReservedItemDto;
import com.orderflow.common.event.inventory.StockRejectedPayload;
import com.orderflow.common.event.inventory.StockReservedPayload;
import com.orderflow.inventory.application.port.in.StockReservationUseCase;
import com.orderflow.inventory.application.port.out.InventoryEventPublisherPort;
import com.orderflow.inventory.application.port.out.ProcessedOrderReservationPort;
import com.orderflow.inventory.application.port.out.ProductRepositoryPort;
import com.orderflow.inventory.domain.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Application service coordinating atomic stock reservations, Saga compensation releases, and
 * order confirmations.
 */
@Service
public class StockReservationService implements StockReservationUseCase {

    private static final Logger log = LoggerFactory.getLogger(StockReservationService.class);

    private final ProductRepositoryPort productRepositoryPort;
    private final InventoryEventPublisherPort eventPublisherPort;
    private final ProcessedOrderReservationPort processedOrderReservationPort;
    private record Failure(FailedProductDto dto, StockRejectionReason reason) {}


    public StockReservationService(
            ProductRepositoryPort productRepositoryPort,
            InventoryEventPublisherPort eventPublisherPort,
            ProcessedOrderReservationPort processedOrderReservationPort
    ) {
        this.productRepositoryPort = productRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
        this.processedOrderReservationPort = processedOrderReservationPort;
    }

    @Override
    @Transactional
    public void reserveStock(ReserveStockCommand command) {
        log.info("Processing stock reservation for order: {}", command.orderId());
        if (processedOrderReservationPort.existsByOrderId(command.orderId())) {
            log.info("Ignoring duplicate stock reservation request for order '{}'", command.orderId());
            return;
        }
        processedOrderReservationPort.markProcessed(command.orderId());

        List<Failure> failures = new ArrayList<>();
        Map<Product, Integer> productsToReserve = new LinkedHashMap<>();

        // Phase 1: Pre-validation of all requested items (Atomic Check)
        for (OrderItemRequest item : command.items()) {
            Optional<Product> productOpt = productRepositoryPort.findById(item.productId());
            if (productOpt.isEmpty()) {
                log.warn("Product '{}' not found during reservation for order '{}'", item.productId(), command.orderId());
                failures.add(new Failure(
                        new FailedProductDto(item.productId(), item.quantity(), 0),
                        StockRejectionReason.PRODUCT_NOT_FOUND));
                continue;
            }
            Product product = productOpt.get();
            if (!product.isActive()) {
                log.warn("Product '{}' is inactive for order '{}'", item.productId(), command.orderId());
                failures.add(new Failure(
                        new FailedProductDto(item.productId(), item.quantity(), product.getAvailableQuantity()),
                        StockRejectionReason.PRODUCT_INACTIVE));
                continue;
            }
            if (product.getAvailableQuantity() < item.quantity()) {
                log.warn("Insufficient stock for product '{}' (requested: {}, available: {}) for order '{}'",
                        item.productId(), item.quantity(), product.getAvailableQuantity(), command.orderId());
                failures.add(new Failure(
                        new FailedProductDto(item.productId(), item.quantity(), product.getAvailableQuantity()),
                        StockRejectionReason.INSUFFICIENT_STOCK));
                continue;
            }
            productsToReserve.put(product, item.quantity());
        }

        // Phase 2: Handle Failure (Publish rejection to Kafka, abort DB mutations)
        if (!failures.isEmpty()) {
            log.info("Stock reservation failed for order '{}'. Publishing StockRejected event", command.orderId());
            eventPublisherPort.publishStockRejected(new StockRejectedPayload(
                    command.orderId(),
                    failures.getFirst().reason().name(),
                    failures.stream().map(Failure::dto).toList()));
            return;
        }

        // Phase 3: Apply Reservation and Persist
        List<ReservedItemDto> reservedItems = new ArrayList<>();
        for (Map.Entry<Product, Integer> entry : productsToReserve.entrySet()) {
            Product product = entry.getKey();
            int quantity = entry.getValue();
            product.reserveStock(quantity);
            productRepositoryPort.save(product);
            reservedItems.add(new ReservedItemDto(product.getId(), quantity));
        }

        String reservationId = UUID.randomUUID().toString();
        log.info("Stock successfully reserved for order '{}' with reservationId '{}'", command.orderId(), reservationId);
        StockReservedPayload reservedPayload = new StockReservedPayload(
                command.orderId(),
                reservationId,
                reservedItems
        );
        eventPublisherPort.publishStockReserved(reservedPayload);
    }

    @Override
    @Transactional
    public void releaseStock(ReleaseStockCommand command) {
        log.info("Processing stock release (compensation) for order: {}", command.orderId());
        if (command.items() == null || command.items().isEmpty()) {
            return;
        }
        for (OrderItemRequest item : command.items()) {
            productRepositoryPort.findById(item.productId()).ifPresent(product -> {
                product.releaseStock(item.quantity());
                productRepositoryPort.save(product);
                log.info("Released {} reserved units of product '{}' for order '{}'",
                        item.quantity(), product.getId(), command.orderId());
            });
        }
    }

    @Override
    @Transactional
    public void confirmStock(ConfirmStockCommand command) {
        log.info("Processing stock confirmation for order: {}", command.orderId());
        if (command.items() == null || command.items().isEmpty()) {
            return;
        }
        for (OrderItemRequest item : command.items()) {
            productRepositoryPort.findById(item.productId()).ifPresent(product -> {
                product.confirmReservation(item.quantity());
                productRepositoryPort.save(product);
                log.info("Confirmed deduction of {} reserved units of product '{}' for order '{}'",
                        item.quantity(), product.getId(), command.orderId());
            });
        }
    }
}

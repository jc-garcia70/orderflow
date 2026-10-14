package com.orderflow.orders.application.service;

import com.orderflow.common.event.order.*;
import com.orderflow.common.enums.OrderStatus;
import com.orderflow.common.exception.BusinessException;
import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.orders.application.port.in.CreateOrderUseCase;
import com.orderflow.orders.application.port.in.GetOrderUseCase;
import com.orderflow.orders.application.port.in.OrderSagaUseCase;
import com.orderflow.orders.application.port.in.OrderTimeoutUseCase;
import com.orderflow.orders.application.port.out.OrderEventPublisherPort;
import com.orderflow.orders.application.port.out.OrderRepositoryPort;
import com.orderflow.orders.application.port.out.ProductCatalogPort;
import com.orderflow.orders.domain.model.Order;
import com.orderflow.orders.domain.model.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.*;


@Service
public class OrderService implements CreateOrderUseCase,
        OrderSagaUseCase, GetOrderUseCase, OrderTimeoutUseCase {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final OrderRepositoryPort orderRepositoryPort;
    private final OrderEventPublisherPort orderEventPublisherPort;
    private final ProductCatalogPort productCatalogPort;
    private final TransactionOperations transactionOperations;

    static final String TIMEOUT_REASON = "ORDER_TIMEOUT";

    public OrderService(OrderRepositoryPort orderRepositoryPort,
                        OrderEventPublisherPort orderEventPublisherPort,
                        ProductCatalogPort productCatalogPort,
                        TransactionOperations transactionOperations) {
        this.orderRepositoryPort = orderRepositoryPort;
        this.orderEventPublisherPort = orderEventPublisherPort;
        this.productCatalogPort = productCatalogPort;
        this.transactionOperations = transactionOperations;
    }


    @Override
    public Order createOrder(CreateOrderCommand command) {
        log.info("Creating new order for user: {}", command.userId());

        // 1. Network calls to inventory: no transaction, no DB connection held
        Map<String, ProductCatalogPort.ProductDetails> catalog = loadCatalog(command.items());

        List<OrderItem> domainItems = command.items().stream()
                .map(item -> {
                    ProductCatalogPort.ProductDetails product = catalog.get(item.productId());
                    if (!product.active()) {
                        throw new BusinessException("Product '" + item.productId() + "' is inactive");
                    }
                    return new OrderItem(item.productId(), item.quantity(), product.price());
                })
                .toList();

        Order order = new Order(command.userId(), domainItems);

        // 2. Short transaction: only the order and its outbox event
        return transactionOperations.execute(status -> persistAndPublish(order));
    }


    @Override
    @Transactional(readOnly = true)
    public Order getOrderById(String id) {

        return orderRepositoryPort.findById(id)
                .orElseThrow( () -> new ResourceNotFoundException("Order",id));

    }

    @Override
    public Page<Order> getOrdersByUserId(String userId, Pageable pageable) {

        return orderRepositoryPort.findByUserId(userId,pageable);

    }

    @Override
    public Page<Order> getAllOrders(Pageable pageable) {

        return orderRepositoryPort.findAll(pageable);

    }

    @Override
    @Transactional
    public void handleStockReserved(String orderId) {
        log.info("Processing stock reservation success for order: {}", orderId);
        Order order = getOrderForUpdate(orderId);

        switch (order.getStatus()) {
            case PENDING -> confirmAndPublish(order);
            case CONFIRMED ->
                    log.info("Ignoring duplicate stock reservation event for confirmed order '{}'", orderId);
            case CANCELLED -> {
                log.warn("Stock reserved for already cancelled order '{}'. Requesting release.", orderId);
                orderEventPublisherPort.publishStockReleaseRequested(new StockReleaseRequestedPayload(
                        orderId,
                        Objects.requireNonNullElse(order.getCancellationReason(), "ORDER_CANCELLED")));
            }
        }
    }

    @Override
    @Transactional
    public void handleStockRejected(String orderId, String reason) {
        log.info("Processing stock reservation failure for order: '{}'. Reason: {}", orderId, reason);
        Order order = getOrderForUpdate(orderId);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Ignoring stock rejection for already cancelled order '{}'", orderId);
            return;
        }
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.warn("Ignoring stock rejection for confirmed order '{}' (inconsistent event ordering)", orderId);
            return;
        }
        cancelAndPublish(order, reason);
    }


    @Override
    @Transactional
    public List<String> findStalePendingOrderIds(Duration timeout, int limit) {
        return orderRepositoryPort.findPendingIdsCreatedBefore(Instant.now().minus(timeout),limit);
    }

    @Override
    @Transactional
    public void cancelIfStillPending(String orderId) {
        Optional<Order> found = orderRepositoryPort.findByIdForUpdate(orderId);
        if (found.isEmpty() || found.get().getStatus() != OrderStatus.PENDING) {
            log.info("Order '{}' is no longer pending; timeout cancellation skipped", orderId);
            return;
        }
        log.warn("Order '{}' timed out while PENDING. Cancelling.", orderId);
        cancelAndPublish(found.get(), TIMEOUT_REASON);
    }

    private Order getOrderForUpdate(String orderId) {
        return orderRepositoryPort.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    private void confirmAndPublish(Order order) {
        order.confirm();
        Order updated = orderRepositoryPort.save(order);
        log.info("Order '{}' successfully confirmed. Publishing OrderConfirmedEvent...", updated.getId());
        orderEventPublisherPort.publishOrderConfirmed(new OrderConfirmedPayload(
                updated.getId(), updated.getUserId(), updated.getStatus(), updated.getTotalAmount()));
    }

    private void cancelAndPublish(Order order, String reason) {
        order.cancel(reason);
        Order updated = orderRepositoryPort.save(order);
        log.info("Order '{}' marked as CANCELLED. Publishing OrderCancelledEvent...", updated.getId());
        orderEventPublisherPort.publishOrderCancelled(new OrderCancelledPayload(
                updated.getId(), updated.getUserId(), updated.getStatus(), reason));
    }

    private Map<String, ProductCatalogPort.ProductDetails> loadCatalog(List<OrderItemCommand> items) {
        Map<String, ProductCatalogPort.ProductDetails> catalog = new LinkedHashMap<>();
        for (OrderItemCommand item : items) {
            catalog.computeIfAbsent(item.productId(), productCatalogPort::getById);
        }
        return catalog;
    }

    private Order persistAndPublish(Order order) {
        Order savedOrder = orderRepositoryPort.save(order);

        log.info("Order '{}' created in PENDING status. Publishing OrderCreatedEvent to initiate Saga...",
                savedOrder.getId());

        List<OrderItemDto> itemDtos = savedOrder.getItems().stream()
                .map(item -> new OrderItemDto(item.getProductId(), item.getQuantity(), item.getUnitPrice()))
                .toList();

        orderEventPublisherPort.publishOrderCreated(new OrderCreatedPayload(
                savedOrder.getId(), savedOrder.getUserId(), itemDtos, savedOrder.getTotalAmount()));
        return savedOrder;
    }


}

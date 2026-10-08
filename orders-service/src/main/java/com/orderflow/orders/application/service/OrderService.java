package com.orderflow.orders.application.service;

import com.orderflow.common.event.order.OrderCancelledPayload;
import com.orderflow.common.event.order.OrderConfirmedPayload;
import com.orderflow.common.event.order.OrderCreatedPayload;
import com.orderflow.common.event.order.OrderItemDto;
import com.orderflow.common.enums.OrderStatus;
import com.orderflow.common.exception.BusinessException;
import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.orders.application.port.in.CreateOrderUseCase;
import com.orderflow.orders.application.port.in.GetOrderUseCase;
import com.orderflow.orders.application.port.in.OrderSagaUseCase;
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

import java.util.List;
import java.util.Objects;


@Service
public class OrderService implements CreateOrderUseCase, OrderSagaUseCase, GetOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final OrderRepositoryPort orderRepositoryPort;
    private final OrderEventPublisherPort orderEventPublisherPort;
    private final ProductCatalogPort productCatalogPort;

    public OrderService(OrderRepositoryPort orderRepositoryPort, OrderEventPublisherPort orderEventPublisherPort, ProductCatalogPort productCatalogPort) {
        this.orderRepositoryPort = orderRepositoryPort;
        this.orderEventPublisherPort = orderEventPublisherPort;
        this.productCatalogPort = productCatalogPort;
    }


    @Override
    @Transactional
    public Order createOrder(CreateOrderCommand command) {
        log.info("Creating new order for user: {}",command.userId());

        List<OrderItem> domainItems = command.items().stream()
                .map(item -> {
                    ProductCatalogPort.ProductDetails product =
                            productCatalogPort.getById(item.productId());

                    if (!product.active()) {
                        throw new BusinessException(
                                "Product '" + item.productId() + "' is inactive"
                        );
                    }

                    return new OrderItem(
                            item.productId(),
                            item.quantity(),
                            product.price()
                    );
                })
                .toList();

        Order order = new Order(command.userId(), domainItems);
        Order savedOrder = orderRepositoryPort.save(order);

        log.info("Order '{}' created in PENDING status. Publishing OrderCreatedEvent to initiate " +
                "Saga...", savedOrder.getId());

        List<OrderItemDto> itemDtos = savedOrder.getItems().stream()
                .map(item -> new OrderItemDto(item.getProductId(), item.getQuantity(), item.getUnitPrice()))
                .toList();

        OrderCreatedPayload payload = new OrderCreatedPayload(
                savedOrder.getId(),
                savedOrder.getUserId(),
                itemDtos,
                savedOrder.getTotalAmount()
        );

        orderEventPublisherPort.publishOrderCreated(payload);
        return savedOrder;
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

        Order order = getOrderById(orderId);
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("Ignoring duplicate stock reservation event for confirmed order '{}'", orderId);
            return;
        }

        order.confirm();
        Order updatedOrder = orderRepositoryPort.save(order);

        log.info("Order '{}' successfully confirmed. Publishing OrderConfirmedEvent...", orderId);

        OrderConfirmedPayload payload = new OrderConfirmedPayload(
                updatedOrder.getId(),
                updatedOrder.getUserId(),
                updatedOrder.getStatus(),
                updatedOrder.getTotalAmount()
        );

        orderEventPublisherPort.publishOrderConfirmed(payload);
    }

    @Override
    @Transactional
    public void handleStockRejected(String orderId, String reason) {
        log.info("Processing stock reservation failure for order: '{}'. Reason: {}", orderId,reason);

        Order order = getOrderById(orderId);
        if (order.getStatus() == OrderStatus.CANCELLED
                && Objects.equals(reason, order.getCancellationReason())) {
            log.info("Ignoring duplicate stock rejection event for cancelled order '{}'", orderId);
            return;
        }

        order.cancel(reason);
        Order updatedOrder = orderRepositoryPort.save(order);

        log.info("Order '{}' marked as CANCELLED (Saga compensation). Publishing OrderCancelledEvent...",orderId);

        OrderCancelledPayload payload = new OrderCancelledPayload(
                updatedOrder.getId(),
                updatedOrder.getUserId(),
                updatedOrder.getStatus(),
                reason
        );

        orderEventPublisherPort.publishOrderCancelled(payload);
    }


}

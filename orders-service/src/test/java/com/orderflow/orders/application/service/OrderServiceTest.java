package com.orderflow.orders.application.service;

import com.orderflow.common.enums.OrderStatus;
import com.orderflow.common.event.order.OrderCreatedPayload;
import com.orderflow.common.exception.BusinessException;
import com.orderflow.orders.application.port.in.CreateOrderUseCase;
import com.orderflow.orders.application.port.out.OrderEventPublisherPort;
import com.orderflow.orders.application.port.out.OrderRepositoryPort;
import com.orderflow.orders.application.port.out.ProductCatalogPort;
import com.orderflow.orders.domain.model.Order;
import com.orderflow.orders.domain.model.OrderItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepositoryPort orderRepositoryPort;

    @Mock
    private OrderEventPublisherPort orderEventPublisherPort;

    @Mock
    private ProductCatalogPort productCatalogPort;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepositoryPort,
                orderEventPublisherPort,
                productCatalogPort
        );
    }

    @Test
    void createOrder_UsesInventoryPriceInsteadOfClientInput() {
        BigDecimal catalogPrice = new BigDecimal("19.95");
        when(productCatalogPort.getById("prod-1"))
                .thenReturn(new ProductCatalogPort.ProductDetails(catalogPrice, true));
        when(orderRepositoryPort.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId("ord-1");
            return order;
        });

        Order created = orderService.createOrder(new CreateOrderUseCase.CreateOrderCommand(
                "usr-1",
                List.of(new CreateOrderUseCase.OrderItemCommand("prod-1", 2))
        ));

        assertThat(created.getItems()).singleElement()
                .extracting(OrderItem::getUnitPrice)
                .isEqualTo(catalogPrice);
        assertThat(created.getTotalAmount()).isEqualByComparingTo("39.90");

        ArgumentCaptor<OrderCreatedPayload> payloadCaptor =
                ArgumentCaptor.forClass(OrderCreatedPayload.class);
        verify(orderEventPublisherPort).publishOrderCreated(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().items().getFirst().unitPrice())
                .isEqualByComparingTo(catalogPrice);
    }

    @Test
    void createOrder_RejectsInactiveProductBeforePersisting() {
        when(productCatalogPort.getById("prod-1"))
                .thenReturn(new ProductCatalogPort.ProductDetails(new BigDecimal("19.95"), false));

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderUseCase.CreateOrderCommand(
                "usr-1",
                List.of(new CreateOrderUseCase.OrderItemCommand("prod-1", 1))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inactive");

        verify(orderRepositoryPort, never()).save(any(Order.class));
        verifyNoInteractions(orderEventPublisherPort);
    }

    @Test
    void handleStockReserved_IgnoresDuplicateEventForConfirmedOrder() {
        Order confirmedOrder = existingOrder("ord-1", OrderStatus.CONFIRMED, null);
        when(orderRepositoryPort.findById("ord-1")).thenReturn(Optional.of(confirmedOrder));

        orderService.handleStockReserved("ord-1");

        verify(orderRepositoryPort, never()).save(any(Order.class));
        verifyNoInteractions(orderEventPublisherPort);
    }

    @Test
    void handleStockRejected_IgnoresDuplicateEventForCancelledOrder() {
        Order cancelledOrder = existingOrder(
                "ord-1",
                OrderStatus.CANCELLED,
                "INSUFFICIENT_STOCK"
        );
        when(orderRepositoryPort.findById("ord-1")).thenReturn(Optional.of(cancelledOrder));

        orderService.handleStockRejected("ord-1", "INSUFFICIENT_STOCK");

        verify(orderRepositoryPort, never()).save(any(Order.class));
        verifyNoInteractions(orderEventPublisherPort);
    }

    private Order existingOrder(String id, OrderStatus status, String cancellationReason) {
        return new Order(
                id,
                "usr-1",
                status,
                List.of(new OrderItem("prod-1", 1, new BigDecimal("10.00"))),
                new BigDecimal("10.00"),
                cancellationReason,
                Instant.now(),
                Instant.now()
        );
    }
}

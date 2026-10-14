package com.orderflow.orders.application.service;

import com.orderflow.common.enums.OrderStatus;
import com.orderflow.common.event.order.OrderCancelledPayload;
import com.orderflow.common.event.order.OrderCreatedPayload;
import com.orderflow.common.event.order.StockReleaseRequestedPayload;
import com.orderflow.common.exception.BusinessException;
import com.orderflow.orders.application.exception.InventoryServiceUnavailableException;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
                productCatalogPort,
                TransactionOperations.withoutTransaction()
        );
    }

    // ---------- createOrder (sin cambios) ----------

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

    // ---------- handleStockReserved ----------

    @Test
    void handleStockReserved_ConfirmsPendingOrder() {
        Order pending = existingOrder("ord-1", OrderStatus.PENDING, null);
        when(orderRepositoryPort.findByIdForUpdate("ord-1")).thenReturn(Optional.of(pending));
        when(orderRepositoryPort.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.handleStockReserved("ord-1");

        assertThat(pending.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderEventPublisherPort).publishOrderConfirmed(any());
    }

    @Test
    void handleStockReserved_IgnoresDuplicateEventForConfirmedOrder() {
        Order confirmedOrder = existingOrder("ord-1", OrderStatus.CONFIRMED, null);
        when(orderRepositoryPort.findByIdForUpdate("ord-1")).thenReturn(Optional.of(confirmedOrder));

        orderService.handleStockReserved("ord-1");

        verify(orderRepositoryPort, never()).save(any(Order.class));
        verifyNoInteractions(orderEventPublisherPort);
    }

    @Test
    void handleStockReserved_OnCancelledOrder_RequestsStockRelease() {
        Order cancelled = existingOrder("ord-1", OrderStatus.CANCELLED, "ORDER_TIMEOUT");
        when(orderRepositoryPort.findByIdForUpdate("ord-1")).thenReturn(Optional.of(cancelled));

        orderService.handleStockReserved("ord-1");

        ArgumentCaptor<StockReleaseRequestedPayload> captor =
                ArgumentCaptor.forClass(StockReleaseRequestedPayload.class);
        verify(orderEventPublisherPort).publishStockReleaseRequested(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo("ord-1");
        assertThat(captor.getValue().reason()).isEqualTo("ORDER_TIMEOUT");
        verify(orderRepositoryPort, never()).save(any(Order.class));
        verify(orderEventPublisherPort, never()).publishOrderConfirmed(any());
    }

    // ---------- handleStockRejected ----------

    @Test
    void handleStockRejected_CancelsPendingOrder() {
        Order pending = existingOrder("ord-1", OrderStatus.PENDING, null);
        when(orderRepositoryPort.findByIdForUpdate("ord-1")).thenReturn(Optional.of(pending));
        when(orderRepositoryPort.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.handleStockRejected("ord-1", "INSUFFICIENT_STOCK");

        assertThat(pending.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(pending.getCancellationReason()).isEqualTo("INSUFFICIENT_STOCK");
        verify(orderEventPublisherPort).publishOrderCancelled(any());
    }

    @Test
    void handleStockRejected_IgnoresDuplicateEventForCancelledOrder() {
        Order cancelledOrder = existingOrder(
                "ord-1",
                OrderStatus.CANCELLED,
                "INSUFFICIENT_STOCK"
        );
        when(orderRepositoryPort.findByIdForUpdate("ord-1")).thenReturn(Optional.of(cancelledOrder));

        orderService.handleStockRejected("ord-1", "INSUFFICIENT_STOCK");

        verify(orderRepositoryPort, never()).save(any(Order.class));
        verifyNoInteractions(orderEventPublisherPort);
    }

    @Test
    void handleStockRejected_OnCancelledOrderWithOtherReason_IsIgnored() {
        Order cancelled = existingOrder("ord-1", OrderStatus.CANCELLED, "ORDER_TIMEOUT");
        when(orderRepositoryPort.findByIdForUpdate("ord-1")).thenReturn(Optional.of(cancelled));

        orderService.handleStockRejected("ord-1", "INSUFFICIENT_STOCK");

        assertThat(cancelled.getCancellationReason()).isEqualTo("ORDER_TIMEOUT");
        verify(orderRepositoryPort, never()).save(any(Order.class));
        verifyNoInteractions(orderEventPublisherPort);
    }

    @Test
    void handleStockRejected_OnConfirmedOrder_IsIgnoredWithoutError() {
        Order confirmed = existingOrder("ord-1", OrderStatus.CONFIRMED, null);
        when(orderRepositoryPort.findByIdForUpdate("ord-1")).thenReturn(Optional.of(confirmed));

        orderService.handleStockRejected("ord-1", "INSUFFICIENT_STOCK");

        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepositoryPort, never()).save(any(Order.class));
        verifyNoInteractions(orderEventPublisherPort);
    }

    // ---------- timeout ----------

    @Test
    void cancelIfStillPending_CancelsOrderAndPublishesEvent() {
        Order pending = existingOrder("ord-1", OrderStatus.PENDING, null);
        when(orderRepositoryPort.findByIdForUpdate("ord-1")).thenReturn(Optional.of(pending));
        when(orderRepositoryPort.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.cancelIfStillPending("ord-1");

        ArgumentCaptor<OrderCancelledPayload> captor =
                ArgumentCaptor.forClass(OrderCancelledPayload.class);
        verify(orderEventPublisherPort).publishOrderCancelled(captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo("ORDER_TIMEOUT");
        assertThat(captor.getValue().status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelIfStillPending_SkipsOrderThatAlreadyLeftPending() {
        Order confirmed = existingOrder("ord-1", OrderStatus.CONFIRMED, null);
        when(orderRepositoryPort.findByIdForUpdate("ord-1")).thenReturn(Optional.of(confirmed));

        orderService.cancelIfStillPending("ord-1");

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

    @Test
    void createOrder_ResolvesCatalogBeforeOpeningTheTransaction() {
        TransactionOperations tx = mock(TransactionOperations.class);
        doAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null))
                .when(tx).execute(any());
        OrderService service = new OrderService(
                orderRepositoryPort, orderEventPublisherPort, productCatalogPort, tx);

        when(productCatalogPort.getById("prod-1"))
                .thenReturn(new ProductCatalogPort.ProductDetails(new BigDecimal("10.00"), true));
        when(orderRepositoryPort.save(any(Order.class))).thenAnswer(inv -> {
            Order saved = inv.getArgument(0);
            saved.setId("ord-1");
            return saved;
        });

        service.createOrder(new CreateOrderUseCase.CreateOrderCommand(
                "usr-1", List.of(new CreateOrderUseCase.OrderItemCommand("prod-1", 1))));

        InOrder order = inOrder(productCatalogPort, tx, orderRepositoryPort);
        order.verify(productCatalogPort).getById("prod-1");
        order.verify(tx).execute(any());
        order.verify(orderRepositoryPort).save(any(Order.class));
    }

    @Test
    void createOrder_QueriesEachDistinctProductOnlyOnce() {
        when(productCatalogPort.getById("prod-1"))
                .thenReturn(new ProductCatalogPort.ProductDetails(new BigDecimal("10.00"), true));
        when(orderRepositoryPort.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.createOrder(new CreateOrderUseCase.CreateOrderCommand(
                "usr-1",
                List.of(new CreateOrderUseCase.OrderItemCommand("prod-1", 1),
                        new CreateOrderUseCase.OrderItemCommand("prod-1", 2))));

        verify(productCatalogPort, times(1)).getById("prod-1");
    }

    @Test
    void createOrder_WhenInventoryIsUnavailable_PersistsNothing() {
        when(productCatalogPort.getById("prod-1"))
                .thenThrow(new InventoryServiceUnavailableException("down", new RuntimeException()));

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderUseCase.CreateOrderCommand(
                "usr-1", List.of(new CreateOrderUseCase.OrderItemCommand("prod-1", 1)))))
                .isInstanceOf(InventoryServiceUnavailableException.class);

        verify(orderRepositoryPort, never()).save(any(Order.class));
        verifyNoInteractions(orderEventPublisherPort);
    }


}
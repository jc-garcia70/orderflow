package com.orderflow.common.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orderflow.common.enums.OrderStatus;
import com.orderflow.common.event.inventory.FailedProductDto;
import com.orderflow.common.event.inventory.ReservedItemDto;
import com.orderflow.common.event.inventory.StockRejectedPayload;
import com.orderflow.common.event.inventory.StockReservedPayload;
import com.orderflow.common.event.order.OrderCancelledPayload;
import com.orderflow.common.event.order.OrderConfirmedPayload;
import com.orderflow.common.event.order.OrderCreatedPayload;
import com.orderflow.common.event.order.OrderItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventEnvelopeSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Should serialize and deserialize OrderCreatedEvent envelope correctly")
    void testOrderCreatedEventSerialization() throws Exception {
        List<OrderItemDto> items = List.of(
                new OrderItemDto("prod-101", 2, new BigDecimal("49.99")),
                new OrderItemDto("prod-204", 1, new BigDecimal("120.00"))
        );
        OrderCreatedPayload payload = new OrderCreatedPayload(
                "ord-88392",
                "usr-1204",
                items,
                new BigDecimal("219.98")
        );
        EventEnvelope<OrderCreatedPayload> envelope = EventEnvelope.of(
                EventType.ORDER_CREATED,
                payload.orderId(),
                payload
        );

        String json = objectMapper.writeValueAsString(envelope);
        assertThat(json).contains("OrderCreatedEvent");
        assertThat(json).contains("ord-88392");
        assertThat(json).contains("prod-101");

        EventEnvelope<OrderCreatedPayload> deserialized = objectMapper.readValue(
                json,
                new TypeReference<EventEnvelope<OrderCreatedPayload>>() {}
        );

        assertThat(deserialized.eventId()).isEqualTo(envelope.eventId());
        assertThat(deserialized.eventType()).isEqualTo(EventType.ORDER_CREATED);
        assertThat(deserialized.aggregateId()).isEqualTo("ord-88392");
        assertThat(deserialized.payload().orderId()).isEqualTo("ord-88392");
        assertThat(deserialized.payload().items()).hasSize(2);
        assertThat(deserialized.payload().totalAmount()).isEqualByComparingTo("219.98");
    }

    @Test
    @DisplayName("Should serialize and deserialize StockReservedEvent envelope correctly")
    void testStockReservedEventSerialization() throws Exception {
        List<ReservedItemDto> items = List.of(
                new ReservedItemDto("prod-101", 2),
                new ReservedItemDto("prod-204", 1)
        );
        StockReservedPayload payload = new StockReservedPayload("ord-88392", "res-9941", items);
        EventEnvelope<StockReservedPayload> envelope = EventEnvelope.of(
                EventType.STOCK_RESERVED,
                payload.orderId(),
                payload
        );

        String json = objectMapper.writeValueAsString(envelope);
        EventEnvelope<StockReservedPayload> deserialized = objectMapper.readValue(
                json,
                new TypeReference<EventEnvelope<StockReservedPayload>>() {}
        );

        assertThat(deserialized.eventType()).isEqualTo(EventType.STOCK_RESERVED);
        assertThat(deserialized.payload().reservationId()).isEqualTo("res-9941");
        assertThat(deserialized.payload().items()).hasSize(2);
    }

    @Test
    @DisplayName("Should serialize and deserialize StockRejectedEvent envelope correctly")
    void testStockRejectedEventSerialization() throws Exception {
        List<FailedProductDto> failed = List.of(
                new FailedProductDto("prod-101", 2, 1)
        );
        StockRejectedPayload payload = new StockRejectedPayload("ord-88392", "INSUFFICIENT_STOCK", failed);
        EventEnvelope<StockRejectedPayload> envelope = EventEnvelope.of(
                EventType.STOCK_REJECTED,
                payload.orderId(),
                payload
        );

        String json = objectMapper.writeValueAsString(envelope);
        EventEnvelope<StockRejectedPayload> deserialized = objectMapper.readValue(
                json,
                new TypeReference<EventEnvelope<StockRejectedPayload>>() {}
        );

        assertThat(deserialized.eventType()).isEqualTo(EventType.STOCK_RESERVED.replace("Reserved", "Rejected"));
        assertThat(deserialized.payload().reason()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(deserialized.payload().failedProducts().getFirst().availableQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should serialize and deserialize OrderConfirmedEvent envelope correctly")
    void testOrderConfirmedEventSerialization() throws Exception {
        OrderConfirmedPayload payload = new OrderConfirmedPayload(
                "ord-88392",
                "usr-1204",
                OrderStatus.CONFIRMED,
                new BigDecimal("219.98")
        );
        EventEnvelope<OrderConfirmedPayload> envelope = EventEnvelope.of(
                EventType.ORDER_CONFIRMED,
                payload.orderId(),
                payload
        );

        String json = objectMapper.writeValueAsString(envelope);
        EventEnvelope<OrderConfirmedPayload> deserialized = objectMapper.readValue(
                json,
                new TypeReference<EventEnvelope<OrderConfirmedPayload>>() {}
        );

        assertThat(deserialized.payload().status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Should serialize and deserialize OrderCancelledEvent envelope correctly")
    void testOrderCancelledEventSerialization() throws Exception {
        OrderCancelledPayload payload = new OrderCancelledPayload(
                "ord-88392",
                "usr-1204",
                OrderStatus.CANCELLED,
                "INSUFFICIENT_STOCK"
        );
        EventEnvelope<OrderCancelledPayload> envelope = EventEnvelope.of(
                EventType.ORDER_CANCELLED,
                payload.orderId(),
                payload
        );

        String json = objectMapper.writeValueAsString(envelope);
        EventEnvelope<OrderCancelledPayload> deserialized = objectMapper.readValue(
                json,
                new TypeReference<EventEnvelope<OrderCancelledPayload>>() {}
        );

        assertThat(deserialized.payload().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(deserialized.payload().reason()).isEqualTo("INSUFFICIENT_STOCK");
    }
}

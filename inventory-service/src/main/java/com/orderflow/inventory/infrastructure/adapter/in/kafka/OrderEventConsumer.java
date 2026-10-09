package com.orderflow.inventory.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.common.event.EventEnvelope;
import com.orderflow.common.event.EventType;
import com.orderflow.common.event.KafkaTopics;
import com.orderflow.common.event.order.OrderCreatedPayload;
import com.orderflow.inventory.application.port.in.StockReservationUseCase;
import com.orderflow.inventory.application.port.in.StockReservationUseCase.OrderItemRequest;
import com.orderflow.inventory.application.port.in.StockReservationUseCase.ReserveStockCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.util.List;

/**
 * Inbound messaging adapter listening to order lifecycle events from kafka.
 * Triggers stock reservation when a new order is created.
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);
    private final StockReservationUseCase stockReservationUseCase;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(StockReservationUseCase stockReservationUseCase, ObjectMapper objectMapper) {
        this.stockReservationUseCase = stockReservationUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_EVENTS, groupId = "${spring.kafka.consumer.group-id:inventory-group}")
    public void consumeOrderEvent(String message) throws JsonProcessingException {
        JsonNode rootNode = objectMapper.readTree(message);
        if (rootNode == null || !rootNode.hasNonNull("eventType")) {
            throw new IllegalArgumentException("Order event is missing eventType");
        }

        String eventType = rootNode.path("eventType").asText();
        if (!StringUtils.hasText(eventType)) {
            throw new IllegalArgumentException("Order event eventType must not be blank");
        }

        log.info("Received Kafka event '{}' from topic '{}'", eventType, KafkaTopics.ORDER_EVENTS);
        switch (eventType) {
            case EventType.ORDER_CREATED -> {
                EventEnvelope<OrderCreatedPayload> envelope = objectMapper.readValue(
                        message, new TypeReference<EventEnvelope<OrderCreatedPayload>>() {});
                if (envelope.payload() == null) {
                    throw new IllegalArgumentException("OrderCreated event payload must not be null");
                }
                handleOrderCreated(envelope.payload());
            }
            case EventType.ORDER_CONFIRMED ->
                    stockReservationUseCase.confirmStock(
                            new StockReservationUseCase.ConfirmStockCommand(requiredAggregateId(rootNode)));
            case EventType.ORDER_CANCELLED ->
                    stockReservationUseCase.releaseStock(
                            new StockReservationUseCase.ReleaseStockCommand(requiredAggregateId(rootNode)));
            default -> log.debug("Ignoring event type '{}' in inventory service", eventType);
        }
    }

    private String requiredAggregateId(JsonNode rootNode) {
        String aggregateId = rootNode.path("aggregateId").asText();
        if (!StringUtils.hasText(aggregateId)) {
            throw new IllegalArgumentException("Order event aggregateId must not be blank");
        }
        return aggregateId;
    }

    private void handleOrderCreated(OrderCreatedPayload payload) {
        log.info("Handling OrderCreatedEvent for order '{}' with {} items", payload.orderId(), payload.items().size());
        List<StockReservationUseCase.OrderItemRequest> itemRequests = payload.items().stream()
                .map(item -> new StockReservationUseCase.OrderItemRequest(item.productId(), item.quantity()))
                .toList();
        stockReservationUseCase.reserveStock(new StockReservationUseCase.ReserveStockCommand(payload.orderId(), itemRequests));
    }


}

package com.orderflow.orders.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.common.event.EventEnvelope;
import com.orderflow.common.event.EventType;
import com.orderflow.common.event.KafkaTopics;
import com.orderflow.common.event.inventory.StockRejectedPayload;
import com.orderflow.common.event.inventory.StockReservedPayload;
import com.orderflow.orders.application.port.in.OrderSagaUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final OrderSagaUseCase orderSagaUseCase;
    private final ObjectMapper objectMapper;

    public InventoryEventConsumer(OrderSagaUseCase orderSagaUseCase, ObjectMapper objectMapper) {
        this.orderSagaUseCase = orderSagaUseCase;
        this.objectMapper = objectMapper;
    }


    @KafkaListener(topics = KafkaTopics.INVENTORY_EVENTS, groupId = "${spring.kafka.consumer.group-id:orders-group}")
    public void consumeInventoryEvent(String message) {
        try {
            JsonNode rootNode = objectMapper.readTree(message);
            String eventType = rootNode.path("eventType").asText();

            log.info("Received Kafka event '{}' from topic '{}'", eventType, KafkaTopics.INVENTORY_EVENTS);

            if (EventType.STOCK_RESERVED.equals(eventType)) {
                EventEnvelope<StockReservedPayload> envelope = objectMapper.readValue(
                        message,
                        new TypeReference<EventEnvelope<StockReservedPayload>>() {}
                );

                handleStockReserved(envelope);

            } else if (EventType.STOCK_REJECTED.equals(eventType)) {
                EventEnvelope<StockRejectedPayload> envelope = objectMapper.readValue(
                        message,
                        new TypeReference<EventEnvelope<StockRejectedPayload>>() {}
                );

                handleStockRejected(envelope);

            } else {
                log.debug("Ignoring event type '{}' in orders service", eventType);
            }

        } catch (Exception e) {
            log.error("Error processing message from topic '{}': {}", KafkaTopics.INVENTORY_EVENTS, message, e);
        }

    }

    private void handleStockReserved(EventEnvelope<StockReservedPayload> envelope) {
        String orderId = envelope.payload().orderId();
        log.info("Processing stock reservation success for order '{}'", orderId);
        orderSagaUseCase.handleStockReserved(orderId);
    }

    private void handleStockRejected(EventEnvelope<StockRejectedPayload> envelope) {
        String orderId = envelope.payload().orderId();
        String reason = envelope.payload().reason();

        log.info("Processing stock rejection for order '{}'. Reason: {}", orderId, reason);
        orderSagaUseCase.handleStockRejected(orderId, reason);
    }

}

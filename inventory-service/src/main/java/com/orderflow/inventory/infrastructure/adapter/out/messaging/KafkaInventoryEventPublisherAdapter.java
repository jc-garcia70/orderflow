package com.orderflow.inventory.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.common.event.EventEnvelope;
import com.orderflow.common.event.EventType;
import com.orderflow.common.event.KafkaTopics;
import com.orderflow.common.event.inventory.StockRejectedPayload;
import com.orderflow.common.event.inventory.StockReservedPayload;
import com.orderflow.inventory.application.port.out.InventoryEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Secondary adapter implementing InventoryEventPublisherPort using Spring Kafka.
 * Serializes domain events wrapped in standard EventEnvelope and publishes to Kafka topics.
 */
@Component
public class KafkaInventoryEventPublisherAdapter implements InventoryEventPublisherPort{

    private static final Logger log = LoggerFactory.getLogger(KafkaInventoryEventPublisherAdapter.class);
    private final KafkaTemplate<String,String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaInventoryEventPublisherAdapter(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }


    @Override
    public void publishStockReserved(StockReservedPayload payload) {
        EventEnvelope<StockReservedPayload> envelope = EventEnvelope.of(
                EventType.STOCK_RESERVED,
                payload.orderId(),
                payload
        );
        sendEvent(KafkaTopics.INVENTORY_EVENTS, payload.orderId(), envelope);
    }

    @Override
    public void publishStockRejected(StockRejectedPayload payload) {
        EventEnvelope<StockRejectedPayload> envelope = EventEnvelope.of(
                EventType.STOCK_REJECTED,
                payload.orderId(),
                payload
        );
        sendEvent(KafkaTopics.INVENTORY_EVENTS, payload.orderId(), envelope);
    }


    private <T> void sendEvent(String topic, String key, EventEnvelope<T> envelope) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(envelope);
            log.info("Publishing event '{}' to topic '{}' with key '{}'", envelope.eventType(), topic, key);
            kafkaTemplate.send(topic, key, jsonPayload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event '{}' for aggregate '{}'", envelope.eventType(), key, e);
            throw new IllegalStateException("Error serializing Kafka event", e);
        }
    }


}

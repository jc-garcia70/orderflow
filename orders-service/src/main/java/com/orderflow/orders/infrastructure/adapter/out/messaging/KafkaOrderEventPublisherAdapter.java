package com.orderflow.orders.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.common.event.EventEnvelope;
import com.orderflow.common.event.EventType;
import com.orderflow.common.event.KafkaTopics;
import com.orderflow.common.event.order.OrderCancelledPayload;
import com.orderflow.common.event.order.OrderConfirmedPayload;
import com.orderflow.common.event.order.OrderCreatedPayload;
import com.orderflow.orders.application.port.out.OrderEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;


/**
 * Secondary adapter implementing OrderEventPublisherPort using Spring Kafka.
 * Publishes order lifecycle events wrapped in EventEnvelope to Kafka topics.
 */
@Component
public class KafkaOrderEventPublisherAdapter implements OrderEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderEventPublisherAdapter.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaOrderEventPublisherAdapter(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishOrderCreated(OrderCreatedPayload payload) {

        EventEnvelope<OrderCreatedPayload> envelope = EventEnvelope.of(
                EventType.ORDER_CREATED,
                payload.orderId(),
                payload
        );
        sendEvent(KafkaTopics.ORDER_EVENTS, payload.orderId(), envelope);

    }

    @Override
    public void publishOrderConfirmed(OrderConfirmedPayload payload) {

        EventEnvelope<OrderConfirmedPayload> envelope = EventEnvelope.of(
                EventType.ORDER_CONFIRMED,
                payload.orderId(),
                payload
        );
        sendEvent(KafkaTopics.ORDER_EVENTS, payload.orderId(), envelope);

    }

    @Override
    public void publishOrderCancelled(OrderCancelledPayload payload) {

        EventEnvelope<OrderCancelledPayload> envelope = EventEnvelope.of(
                EventType.ORDER_CANCELLED,
                payload.orderId(),
                payload
        );
        sendEvent(KafkaTopics.ORDER_EVENTS, payload.orderId(), envelope);

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

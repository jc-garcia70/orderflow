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
import com.orderflow.orders.infrastructure.adapter.out.messaging.outbox.OrderOutboxEntity;
import com.orderflow.orders.infrastructure.adapter.out.messaging.outbox.OrderOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Persists order events in the same database transaction as the order change.
 */
@Component
public class OutboxOrderEventPublisherAdapter implements OrderEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(OutboxOrderEventPublisherAdapter.class);

    private final OrderOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxOrderEventPublisherAdapter(
            OrderOutboxRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishOrderCreated(OrderCreatedPayload payload) {
        enqueue(EventType.ORDER_CREATED, payload.orderId(), payload);
    }

    @Override
    public void publishOrderConfirmed(OrderConfirmedPayload payload) {
        enqueue(EventType.ORDER_CONFIRMED, payload.orderId(), payload);
    }

    @Override
    public void publishOrderCancelled(OrderCancelledPayload payload) {
        enqueue(EventType.ORDER_CANCELLED, payload.orderId(), payload);
    }

    private <T> void enqueue(String eventType, String orderId, T payload) {
        EventEnvelope<T> envelope = EventEnvelope.of(eventType, orderId, payload);
        try {
            String serializedEvent = objectMapper.writeValueAsString(envelope);
            outboxRepository.save(OrderOutboxEntity.pending(
                    envelope.eventId(),
                    KafkaTopics.ORDER_EVENTS,
                    orderId,
                    serializedEvent,
                    Instant.now()
            ));
            log.info("Stored event '{}' in outbox for order '{}'", eventType, orderId);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize event '{}' for order '{}'", eventType, orderId, ex);
            throw new IllegalStateException("Error serializing order event", ex);
        }
    }
}

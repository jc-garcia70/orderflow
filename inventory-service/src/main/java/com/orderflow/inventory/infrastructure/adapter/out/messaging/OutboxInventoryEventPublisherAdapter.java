package com.orderflow.inventory.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.common.event.EventEnvelope;
import com.orderflow.common.event.EventType;
import com.orderflow.common.event.KafkaTopics;
import com.orderflow.common.event.inventory.StockRejectedPayload;
import com.orderflow.common.event.inventory.StockReservedPayload;
import com.orderflow.inventory.application.port.out.InventoryEventPublisherPort;
import com.orderflow.inventory.infrastructure.adapter.out.messaging.outbox.InventoryOutboxEntity;
import com.orderflow.inventory.infrastructure.adapter.out.messaging.outbox.InventoryOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Stores inventory events in the same transaction as the stock change.
 * MANDATORY: publishing outside a transaction would break atomicity, so it fails fast.
 */
@Component
@ConditionalOnProperty(name = "inventory.events.publisher", havingValue = "outbox", matchIfMissing = true)
@Transactional(propagation = Propagation.MANDATORY)
public class OutboxInventoryEventPublisherAdapter implements InventoryEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(OutboxInventoryEventPublisherAdapter.class);

    private final InventoryOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxInventoryEventPublisherAdapter(InventoryOutboxRepository outboxRepository,
                                                ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishStockReserved(StockReservedPayload payload) {
        enqueue(EventType.STOCK_RESERVED, payload.orderId(), payload);
    }

    @Override
    public void publishStockRejected(StockRejectedPayload payload) {
        enqueue(EventType.STOCK_REJECTED, payload.orderId(), payload);
    }

    private <T> void enqueue(String eventType, String orderId, T payload) {
        EventEnvelope<T> envelope = EventEnvelope.of(eventType, orderId, payload);
        try {
            outboxRepository.save(InventoryOutboxEntity.pending(
                    envelope.eventId(),
                    KafkaTopics.INVENTORY_EVENTS,
                    orderId,
                    objectMapper.writeValueAsString(envelope),
                    Instant.now()));
            log.info("Stored event '{}' in outbox for order '{}'", eventType, orderId);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Error serializing inventory event", ex);
        }
    }

}

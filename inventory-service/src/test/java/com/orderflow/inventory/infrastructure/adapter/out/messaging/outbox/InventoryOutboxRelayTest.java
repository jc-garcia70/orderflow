package com.orderflow.inventory.infrastructure.adapter.out.messaging.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryOutboxRelayTest {

    @Mock
    private InventoryOutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private InventoryOutboxRelay relay(long sendTimeoutMs) {
        return new InventoryOutboxRelay(outboxRepository, kafkaTemplate, sendTimeoutMs, 30L, 50);
    }

    private InventoryOutboxEntity event(String id, String key) {
        return InventoryOutboxEntity.pending(id, "inventory-events", key, "{}", Instant.now());
    }

    private void givenPending(InventoryOutboxEntity... events) {
        when(outboxRepository
                .findByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(events));
    }

    @Test
    @DisplayName("Marks the event as published once Kafka acknowledges it")
    void publishesAndMarks() {
        InventoryOutboxEntity e = event("evt-1", "ord-1");
        givenPending(e);
        when(kafkaTemplate.send("inventory-events", "ord-1", "{}"))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        relay(1_000).publishPendingEvents();

        assertThat(e.getPublishedAt()).isNotNull();
        verify(outboxRepository).save(e);
    }

    @Test
    @DisplayName("Schedules a retry and keeps going when one send fails")
    void failureSchedulesRetryAndContinues() {
        InventoryOutboxEntity failing = event("evt-1", "ord-1");
        InventoryOutboxEntity ok = event("evt-2", "ord-2");
        givenPending(failing, ok);

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("record too large"));
        when(kafkaTemplate.send("inventory-events", "ord-1", "{}")).thenReturn(failed);
        when(kafkaTemplate.send("inventory-events", "ord-2", "{}"))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        relay(1_000).publishPendingEvents();

        assertThat(failing.getPublishedAt()).isNull();
        assertThat(failing.getAttempts()).isEqualTo(1);
        assertThat(ok.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("A send timeout stops the batch so locks are not held while the broker is down")
    void timeoutStopsBatch() {
        InventoryOutboxEntity first = event("evt-1", "ord-1");
        InventoryOutboxEntity second = event("evt-2", "ord-2");
        givenPending(first, second);
        when(kafkaTemplate.send("inventory-events", "ord-1", "{}"))
                .thenReturn(new CompletableFuture<>());   // never completes

        relay(10).publishPendingEvents();

        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(second.getAttempts()).isZero();
        verify(kafkaTemplate, never()).send(eq("inventory-events"), eq("ord-2"), any());
    }
}
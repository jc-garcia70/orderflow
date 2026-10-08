package com.orderflow.orders.infrastructure.adapter.out.messaging.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderOutboxRelayTest {

    @Mock
    private OrderOutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void publishPendingEvents_MarksEventPublishedAfterKafkaAcknowledges() {
        OrderOutboxEntity event = pendingEvent();
        when(outboxRepository
                .findTop100ByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any()))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send("order-events", "ord-1", "{\"event\":\"created\"}"))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        new OrderOutboxRelay(outboxRepository, kafkaTemplate, 1_000L, 30L)
                .publishPendingEvents();

        assertThat(event.getPublishedAt()).isNotNull();
        verify(outboxRepository).save(event);
    }

    @Test
    void publishPendingEvents_SchedulesRetryWhenKafkaSendFails() {
        OrderOutboxEntity event = pendingEvent();
        when(outboxRepository
                .findTop100ByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any()))
                .thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> failedSend = new CompletableFuture<>();
        failedSend.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafkaTemplate.send("order-events", "ord-1", "{\"event\":\"created\"}"))
                .thenReturn(failedSend);

        new OrderOutboxRelay(outboxRepository, kafkaTemplate, 1_000L, 30L)
                .publishPendingEvents();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isAfter(event.getCreatedAt());
        assertThat(event.getLastError()).isEqualTo("ExecutionException");
        verify(outboxRepository).save(event);
    }

    private OrderOutboxEntity pendingEvent() {
        return OrderOutboxEntity.pending(
                "evt-1",
                "order-events",
                "ord-1",
                "{\"event\":\"created\"}",
                Instant.now()
        );
    }
}

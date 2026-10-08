package com.orderflow.orders.infrastructure.adapter.out.messaging.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OrderOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OrderOutboxRelay.class);

    private final OrderOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final long sendTimeoutMs;
    private final long publishedRetentionDays;

    public OrderOutboxRelay(
            OrderOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${outbox.kafka-send-timeout-ms:10000}") long sendTimeoutMs,
            @Value("${outbox.published-retention-days:30}") long publishedRetentionDays
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.sendTimeoutMs = sendTimeoutMs;
        this.publishedRetentionDays = publishedRetentionDays;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:1000}")
    public void publishPendingEvents() {
        List<OrderOutboxEntity> pendingEvents =
                outboxRepository
                        .findTop100ByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                                Instant.now()
                        );

        for (OrderOutboxEntity event : pendingEvents) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                        .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
                event.markPublished(Instant.now());
                outboxRepository.save(event);
                log.info("Published outbox event '{}' to topic '{}'", event.getId(), event.getTopic());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                recordFailure(event, ex);
                return;
            } catch (ExecutionException | TimeoutException | KafkaException ex) {
                recordFailure(event, ex);
            }
        }
    }

    @Scheduled(fixedDelayString = "${outbox.cleanup-interval-ms:86400000}")
    public void deleteOldPublishedEvents() {
        Instant cutoff = Instant.now().minusSeconds(publishedRetentionDays * 86_400L);
        long deletedCount = outboxRepository.deleteByPublishedAtBefore(cutoff);
        if (deletedCount > 0) {
            log.info("Deleted {} published outbox events older than '{}'", deletedCount, cutoff);
        }
    }

    private void recordFailure(OrderOutboxEntity event, Exception ex) {
        event.scheduleRetry(Instant.now(), ex.getClass().getSimpleName());
        outboxRepository.save(event);
        log.error(
                "Failed to publish outbox event '{}'; retry scheduled at '{}'",
                event.getId(),
                event.getNextAttemptAt(),
                ex
        );
    }
}

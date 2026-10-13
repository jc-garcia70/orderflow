package com.orderflow.inventory.infrastructure.adapter.out.messaging.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(InventoryOutboxRelay.class);

    private final InventoryOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final long sendTimeoutMs;
    private final long publishedRetentionDays;
    private final int batchSize;

    public InventoryOutboxRelay(
            InventoryOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${outbox.kafka-send-timeout-ms:10000}") long sendTimeoutMs,
            @Value("${outbox.published-retention-days:30}") long publishedRetentionDays,
            @Value("${outbox.batch-size:50}") int batchSize
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.sendTimeoutMs = sendTimeoutMs;
        this.publishedRetentionDays = publishedRetentionDays;
        this.batchSize = batchSize;
    }

    /** Transactional: the SKIP LOCKED row locks live until the batch is processed. */
    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<InventoryOutboxEntity> pending = outboxRepository
                .findByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        Instant.now(), PageRequest.of(0, batchSize));

        for (InventoryOutboxEntity event : pending) {
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
            } catch (TimeoutException ex) {
                // Broker unreachable: waiting for every remaining event would hold locks for minutes
                recordFailure(event, ex);
                return;
            } catch (ExecutionException | KafkaException ex) {
                recordFailure(event, ex);
            }
        }
    }

    @Scheduled(fixedDelayString = "${outbox.cleanup-interval-ms:86400000}")
    public void deleteOldPublishedEvents() {
        Instant cutoff = Instant.now().minusSeconds(publishedRetentionDays * 86_400L);
        long deleted = outboxRepository.deleteByPublishedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} published outbox events older than '{}'", deleted, cutoff);
        }
    }

    private void recordFailure(InventoryOutboxEntity event, Exception ex) {
        event.scheduleRetry(Instant.now(), ex.getClass().getSimpleName());
        outboxRepository.save(event);
        log.error("Failed to publish outbox event '{}'; retry scheduled at '{}'",
                event.getId(), event.getNextAttemptAt(), ex);
    }


}

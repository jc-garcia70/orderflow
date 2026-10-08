package com.orderflow.orders.infrastructure.adapter.out.messaging.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "order_outbox",
        indexes = @Index(
                name = "idx_order_outbox_pending",
                columnList = "published_at,next_attempt_at,created_at"
        )
)
public class OrderOutboxEntity {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(name = "message_key", nullable = false, length = 100)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    protected OrderOutboxEntity() {
    }

    private OrderOutboxEntity(
            String id,
            String topic,
            String messageKey,
            String payload,
            Instant createdAt
    ) {
        this.id = id;
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.createdAt = createdAt;
        this.nextAttemptAt = createdAt;
        this.attempts = 0;
    }

    public static OrderOutboxEntity pending(
            String id,
            String topic,
            String messageKey,
            String payload,
            Instant createdAt
    ) {
        return new OrderOutboxEntity(id, topic, messageKey, payload, createdAt);
    }

    public void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    public void scheduleRetry(Instant attemptedAt, String errorDescription) {
        this.attempts++;
        long delaySeconds = Math.min(300L, 1L << Math.min(this.attempts - 1, 8));
        this.nextAttemptAt = attemptedAt.plusSeconds(delaySeconds);
        this.lastError = errorDescription.length() <= 500
                ? errorDescription
                : errorDescription.substring(0, 500);
    }

    public String getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}

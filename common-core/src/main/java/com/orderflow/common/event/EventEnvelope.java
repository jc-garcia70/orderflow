package com.orderflow.common.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Standard envelope wrapping all OrderFlow domain events transmitted via Kafka.
 * Provides distributed traceability, event identification, and routing metadata.
 *
 * @param <T> Type of the domain payload
 */
public record EventEnvelope<T>(
        @JsonProperty("eventId")
        @NotBlank(message = "eventId must not be blank")
        String eventId,

        @JsonProperty("eventType")
        @NotBlank(message = "eventType must not be blank")
        String eventType,

        @JsonProperty("timestamp")
        @NotNull(message = "timestamp must not be null")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp,

        @JsonProperty("aggregateId")
        @NotBlank(message = "aggregateId must not be blank")
        String aggregateId,

        @JsonProperty("payload")
        @Valid
        @NotNull(message = "payload must not be null")
        T payload
) {
    /**
     * Factory method creating a new EventEnvelope with auto-generated UUID and current UTC timestamp.
     *
     * @param eventType   the event type identifier (see {@link EventType})
     * @param aggregateId the ID of the aggregate root (e.g. orderId)
     * @param payload     the domain event payload
     * @param <T>         the payload type
     * @return a new EventEnvelope instance
     */
    public static <T> EventEnvelope<T> of(String eventType, String aggregateId, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID().toString(),
                eventType,
                Instant.now(),
                aggregateId,
                payload
        );
    }
}

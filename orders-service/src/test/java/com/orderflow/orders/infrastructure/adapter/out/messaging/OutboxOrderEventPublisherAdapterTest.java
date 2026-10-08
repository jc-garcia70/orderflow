package com.orderflow.orders.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orderflow.common.event.KafkaTopics;
import com.orderflow.common.event.order.OrderCreatedPayload;
import com.orderflow.common.event.order.OrderItemDto;
import com.orderflow.orders.infrastructure.adapter.out.messaging.outbox.OrderOutboxEntity;
import com.orderflow.orders.infrastructure.adapter.out.messaging.outbox.OrderOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxOrderEventPublisherAdapterTest {

    @Mock
    private OrderOutboxRepository outboxRepository;

    @Test
    void publishOrderCreated_PersistsSerializedEnvelopeInsteadOfSendingDirectlyToKafka() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        OutboxOrderEventPublisherAdapter adapter =
                new OutboxOrderEventPublisherAdapter(outboxRepository, objectMapper);
        OrderCreatedPayload payload = new OrderCreatedPayload(
                "ord-1",
                "usr-1",
                List.of(new OrderItemDto("prod-1", 2, new BigDecimal("12.50"))),
                new BigDecimal("25.00")
        );

        adapter.publishOrderCreated(payload);

        ArgumentCaptor<OrderOutboxEntity> eventCaptor =
                ArgumentCaptor.forClass(OrderOutboxEntity.class);
        verify(outboxRepository).save(eventCaptor.capture());

        OrderOutboxEntity storedEvent = eventCaptor.getValue();
        var json = objectMapper.readTree(storedEvent.getPayload());
        assertThat(storedEvent.getTopic()).isEqualTo(KafkaTopics.ORDER_EVENTS);
        assertThat(storedEvent.getMessageKey()).isEqualTo("ord-1");
        assertThat(storedEvent.getId()).isEqualTo(json.path("eventId").asText());
        assertThat(json.path("eventType").asText()).isEqualTo("OrderCreatedEvent");
        assertThat(json.path("payload").path("orderId").asText()).isEqualTo("ord-1");
        assertThat(storedEvent.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
    }
}

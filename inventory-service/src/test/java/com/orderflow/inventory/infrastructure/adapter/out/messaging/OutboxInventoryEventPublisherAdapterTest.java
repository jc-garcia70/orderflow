package com.orderflow.inventory.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orderflow.common.event.KafkaTopics;
import com.orderflow.common.event.inventory.FailedProductDto;
import com.orderflow.common.event.inventory.ReservedItemDto;
import com.orderflow.common.event.inventory.StockRejectedPayload;
import com.orderflow.common.event.inventory.StockReservedPayload;
import com.orderflow.inventory.infrastructure.adapter.out.messaging.outbox.InventoryOutboxEntity;
import com.orderflow.inventory.infrastructure.adapter.out.messaging.outbox.InventoryOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxInventoryEventPublisherAdapterTest {

    @Mock
    private InventoryOutboxRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void publishStockReserved_PersistsEnvelopeKeyedByOrderId() throws Exception {
        new OutboxInventoryEventPublisherAdapter(outboxRepository, objectMapper)
                .publishStockReserved(new StockReservedPayload(
                        "ord-1", "res-1", List.of(new ReservedItemDto("p1", 2))));

        InventoryOutboxEntity stored = captureSaved();
        var json = objectMapper.readTree(stored.getPayload());
        assertThat(stored.getTopic()).isEqualTo(KafkaTopics.INVENTORY_EVENTS);
        assertThat(stored.getMessageKey()).isEqualTo("ord-1");
        assertThat(stored.getId()).isEqualTo(json.path("eventId").asText());
        assertThat(json.path("eventType").asText()).isEqualTo("StockReservedEvent");
        assertThat(json.path("aggregateId").asText()).isEqualTo("ord-1");
    }

    @Test
    void publishStockRejected_PersistsReason() throws Exception {
        new OutboxInventoryEventPublisherAdapter(outboxRepository, objectMapper)
                .publishStockRejected(new StockRejectedPayload(
                        "ord-2", "PRODUCT_NOT_FOUND", List.of(new FailedProductDto("p9", 1, 0))));

        InventoryOutboxEntity stored = captureSaved();
        var json = objectMapper.readTree(stored.getPayload());
        assertThat(json.path("eventType").asText()).isEqualTo("StockRejectedEvent");
        assertThat(json.path("payload").path("reason").asText()).isEqualTo("PRODUCT_NOT_FOUND");
    }

    private InventoryOutboxEntity captureSaved() {
        ArgumentCaptor<InventoryOutboxEntity> captor = ArgumentCaptor.forClass(InventoryOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }
}
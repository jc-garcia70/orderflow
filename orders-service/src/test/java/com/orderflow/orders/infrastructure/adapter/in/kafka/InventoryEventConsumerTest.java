package com.orderflow.orders.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orderflow.orders.application.port.in.OrderSagaUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InventoryEventConsumerTest {

    @Mock
    private OrderSagaUseCase orderSagaUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    @DisplayName("Should handle StockReservedEvent and confirm order")
    void consumeInventoryEvent_WhenStockReserved_ShouldCallHandleStockReserved() throws Exception {
        InventoryEventConsumer consumer = new InventoryEventConsumer(orderSagaUseCase, objectMapper);

        String message = """
            {
              "eventId": "evt-001",
              "eventType": "StockReservedEvent",
              "timestamp": "2026-09-25T17:50:00Z",
              "aggregateId": "ord-123",
              "payload": {
                "orderId": "ord-123",
                "reservationId": "res-123",
                "items": [
                  {
                    "productId": "prod-001",
                    "quantityReserved": 2
                  }
                ]
              }
            }
            """;

        consumer.consumeInventoryEvent(message);

        verify(orderSagaUseCase).handleStockReserved("ord-123");
    }

    @Test
    @DisplayName("Should handle StockRejectedEvent and cancel order")
    void consumeInventoryEvent_WhenStockRejected_ShouldCallHandleStockRejected() throws Exception {
        InventoryEventConsumer consumer = new InventoryEventConsumer(orderSagaUseCase, objectMapper);

        String message = """
            {
              "eventId": "evt-002",
              "eventType": "StockRejectedEvent",
              "timestamp": "2026-09-25T17:50:00Z",
              "aggregateId": "ord-123",
              "payload": {
                "orderId": "ord-123",
                "reason": "INSUFFICIENT_STOCK",
                "failedProducts": [
                  {
                    "productId": "prod-001",
                    "requestedQuantity": 2,
                    "availableQuantity": 1
                  }
                ]
              }
            }
            """;

        consumer.consumeInventoryEvent(message);

        verify(orderSagaUseCase).handleStockRejected("ord-123", "INSUFFICIENT_STOCK");
    }
}
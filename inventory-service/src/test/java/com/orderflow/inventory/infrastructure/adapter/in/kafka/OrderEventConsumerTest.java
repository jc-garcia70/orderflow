package com.orderflow.inventory.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orderflow.inventory.application.port.in.StockReservationUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private StockReservationUseCase stockReservationUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void consumeOrderEvent_DelegatesOrderCreatedToReservationUseCase() throws Exception {
        OrderEventConsumer consumer = new OrderEventConsumer(stockReservationUseCase, objectMapper);

        consumer.consumeOrderEvent("""
                {
                  "eventType": "OrderCreatedEvent",
                  "payload": {
                    "orderId": "ord-1",
                    "userId": "usr-1",
                    "items": [{"productId": "prod-1", "quantity": 2, "unitPrice": 15.00}],
                    "totalAmount": 30.00
                  }
                }
                """);

        verify(stockReservationUseCase).reserveStock(
                new StockReservationUseCase.ReserveStockCommand(
                        "ord-1",
                        java.util.List.of(
                                new StockReservationUseCase.OrderItemRequest("prod-1", 2)
                        )
                )
        );
    }

    @Test
    void consumeOrderEvent_PropagatesReservationFailuresForKafkaRetry() {
        OrderEventConsumer consumer = new OrderEventConsumer(stockReservationUseCase, objectMapper);
        String message = """
                {
                  "eventType": "OrderCreatedEvent",
                  "payload": {
                    "orderId": "ord-1",
                    "userId": "usr-1",
                    "items": [{"productId": "prod-1", "quantity": 2, "unitPrice": 15.00}],
                    "totalAmount": 30.00
                  }
                }
                """;
        doThrow(new IllegalStateException("database unavailable"))
                .when(stockReservationUseCase).reserveStock(any());

        assertThatThrownBy(() -> consumer.consumeOrderEvent(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }

    @Test
    void consumeOrderEvent_PropagatesMalformedMessageForDeadLetterHandling() {
        OrderEventConsumer consumer = new OrderEventConsumer(stockReservationUseCase, objectMapper);

        assertThatThrownBy(() -> consumer.consumeOrderEvent("{malformed"))
                .isInstanceOf(JsonProcessingException.class);
    }
}

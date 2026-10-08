package com.orderflow.orders.infrastructure.adapter.in.rest;

import com.orderflow.orders.application.port.in.CreateOrderUseCase;
import com.orderflow.orders.application.port.in.GetOrderUseCase;
import com.orderflow.orders.domain.model.Order;
import com.orderflow.orders.domain.model.OrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateOrderUseCase createOrderUseCase;

    @MockitoBean
    private GetOrderUseCase getOrderUseCase;

    @Test
    @DisplayName("POST /api/v1/orders should create an order successfully")
    void createOrder_ShouldReturn201() throws Exception {
        Order order = new Order("usr-123", List.of(
                new OrderItem("prod-001", 2, new BigDecimal("25.99"))
        ));
        order.setId("ord-123");

        when(createOrderUseCase.createOrder(any(CreateOrderUseCase.CreateOrderCommand.class)))
                .thenReturn(order);

        String requestBody = """
            {
              "userId": "usr-123",
              "items": [
                {
                  "productId": "prod-001",
                  "quantity": 2,
                  "unitPrice": 25.99
                }
              ]
            }
            """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order created successfully"))
                .andExpect(jsonPath("$.data.id").value("ord-123"))
                .andExpect(jsonPath("$.data.userId").value("usr-123"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} should return order by id")
    void getOrderById_ShouldReturnOrder() throws Exception {
        Order order = new Order("usr-456", List.of(
                new OrderItem("prod-100", 1, new BigDecimal("40.50"))
        ));
        order.setId("ord-456");

        when(getOrderUseCase.getOrderById("ord-456")).thenReturn(order);

        mockMvc.perform(get("/api/v1/orders/{id}", "ord-456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("ord-456"))
                .andExpect(jsonPath("$.data.userId").value("usr-456"))
                .andExpect(jsonPath("$.data.items[0].productId").value("prod-100"));
    }

    @Test
    @DisplayName("POST /api/v1/orders should fail validation on invalid payload")
    void createOrder_InvalidPayload_ShouldReturn400() throws Exception {
        String invalidRequest = """
            {
              "userId": "",
              "items": []
            }
            """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed for one or more fields"))
                .andExpect(jsonPath("$.validationErrors.length()").value(2));
    }
}

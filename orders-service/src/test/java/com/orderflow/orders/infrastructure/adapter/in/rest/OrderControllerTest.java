package com.orderflow.orders.infrastructure.adapter.in.rest;

import com.orderflow.orders.application.port.in.CreateOrderUseCase;
import com.orderflow.orders.application.port.in.GetOrderUseCase;
import com.orderflow.orders.domain.model.Order;
import com.orderflow.orders.domain.model.OrderItem;
import com.orderflow.orders.infrastructure.config.security.SecurityConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = {
        "jwt.secret=0123456789abcdef0123456789abcdef",
        "spring.data.web.pageable.max-page-size=100"
})
class OrderControllerTest {

    private static final String JWT_SECRET = "0123456789abcdef0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateOrderUseCase createOrderUseCase;

    @MockitoBean
    private GetOrderUseCase getOrderUseCase;

    @Test
    @DisplayName("Order endpoints should reject requests without a bearer token")
    void ordersEndpoint_WithoutToken_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Order endpoints should reject an invalid bearer token")
    void ordersEndpoint_InvalidToken_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/orders/ord-123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/orders should create an order for the authenticated user")
    void createOrder_ShouldUseAuthenticatedUser() throws Exception {
        Order order = new Order("usr-123", List.of(
                new OrderItem("prod-001", 2, new BigDecimal("25.99"))
        ));
        order.setId("ord-123");

        when(createOrderUseCase.createOrder(any(CreateOrderUseCase.CreateOrderCommand.class)))
                .thenReturn(order);

        String requestBody = """
                {
                  "items": [
                    {
                      "productId": "prod-001",
                      "quantity": 2
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("usr-123", "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order created successfully"))
                .andExpect(jsonPath("$.data.id").value("ord-123"))
                .andExpect(jsonPath("$.data.userId").value("usr-123"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        var commandCaptor =
                org.mockito.ArgumentCaptor.forClass(CreateOrderUseCase.CreateOrderCommand.class);

        verify(createOrderUseCase).createOrder(commandCaptor.capture());
        assertThat(commandCaptor.getValue().userId()).isEqualTo("usr-123");
        assertThat(commandCaptor.getValue().items())
                .containsExactly(new CreateOrderUseCase.OrderItemCommand("prod-001", 2));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} should return an order owned by the authenticated user")
    void getOrderById_OwnOrder_ShouldReturnOrder() throws Exception {
        Order order = new Order("usr-456", List.of(
                new OrderItem("prod-100", 1, new BigDecimal("40.50"))
        ));
        order.setId("ord-456");

        when(getOrderUseCase.getOrderById("ord-456")).thenReturn(order);

        mockMvc.perform(get("/api/v1/orders/{id}", "ord-456")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("usr-456", "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("ord-456"))
                .andExpect(jsonPath("$.data.userId").value("usr-456"))
                .andExpect(jsonPath("$.data.items[0].productId").value("prod-100"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} should deny access to another user's order")
    void getOrderById_OtherUsersOrder_ShouldReturn403() throws Exception {
        Order order = new Order("usr-456", List.of(
                new OrderItem("prod-100", 1, new BigDecimal("40.50"))
        ));
        order.setId("ord-456");

        when(getOrderUseCase.getOrderById("ord-456")).thenReturn(order);

        mockMvc.perform(get("/api/v1/orders/{id}", "ord-456")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("usr-123", "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        verify(getOrderUseCase, never()).getAllOrders(any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/orders should deny access to non-admin users")
    void getAllOrders_NonAdmin_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("usr-123", "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        verify(getOrderUseCase, never()).getAllOrders(any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/orders should allow administrators to list all orders")
    void getAllOrders_Admin_ShouldReturnOrders() throws Exception {
        when(getOrderUseCase.getAllOrders(any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("usr-admin", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(getOrderUseCase).getAllOrders(any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/orders/user/{userId} should allow a user to list their own orders")
    void getOrdersByUserId_OwnOrders_ShouldReturnOrders() throws Exception {
        when(getOrderUseCase.getOrdersByUserId(any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/orders/user/usr-123")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("usr-123", "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(getOrderUseCase).getOrdersByUserId(
                org.mockito.ArgumentMatchers.eq("usr-123"),
                any(Pageable.class)
        );
    }

    @Test
    @DisplayName("GET /api/v1/orders/user/{userId} should deny another user's orders")
    void getOrdersByUserId_OtherUser_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/orders/user/usr-456")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("usr-123", "ROLE_USER")))
                .andExpect(status().isForbidden());

        verify(getOrderUseCase, never())
                .getOrdersByUserId(any(), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/orders should cap requested page size")
    void getAllOrders_Admin_ShouldCapPageSize() throws Exception {
        when(getOrderUseCase.getAllOrders(any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/orders")
                        .param("page", "0")
                        .param("size", "500")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("usr-admin", "ROLE_ADMIN")))
                .andExpect(status().isOk());

        var pageableCaptor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(getOrderUseCase).getAllOrders(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("POST /api/v1/orders should reject an empty items list")
    void createOrder_EmptyItems_ShouldReturn400() throws Exception {
        String invalidRequest = """
                {
                  "items": []
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("usr-123", "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("Validation failed for one or more fields"))
                .andExpect(jsonPath("$.validationErrors.length()").value(1))
                .andExpect(jsonPath("$.validationErrors[0].field").value("items"));
    }

    private String bearerToken(String userId, String role) {
        return "Bearer " + Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}

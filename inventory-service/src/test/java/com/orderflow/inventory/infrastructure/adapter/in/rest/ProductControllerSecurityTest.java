package com.orderflow.inventory.infrastructure.adapter.in.rest;

import com.orderflow.inventory.application.port.in.ProductUseCase;
import com.orderflow.inventory.domain.model.Product;
import com.orderflow.inventory.infrastructure.config.security.SecurityConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = {
        "jwt.secret=" + ProductControllerSecurityTest.USER_SECRET,
        "service-auth.secret=" + ProductControllerSecurityTest.SERVICE_SECRET,
        "service-auth.issuer=orders-service",
        "service-auth.audience=inventory-service"
})
class ProductControllerSecurityTest {

    static final String USER_SECRET = "0123456789abcdef0123456789abcdef";
    static final String SERVICE_SECRET = "fedcba9876543210fedcba9876543210";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductUseCase productUseCase;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product("SKU-1", "Mouse", "Wireless mouse", new BigDecimal("25.00"), 10);
        when(productUseCase.getProductById("p1")).thenReturn(product);
        when(productUseCase.getAllProducts(any())).thenReturn(Page.empty());
        when(productUseCase.createProduct(any())).thenReturn(product);
    }

    @Test
    @DisplayName("Requests without a token are rejected with 401")
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/products/p1")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A regular user can read a product")
    void user_canReadProduct() throws Exception {
        mockMvc.perform(get("/api/v1/products/p1")
                        .header(HttpHeaders.AUTHORIZATION, userToken("usr-1", "ROLE_USER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A regular user cannot create products")
    void user_cannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, userToken("usr-1", "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProductJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("An admin can create products")
    void admin_canCreateProduct() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, userToken("adm-1", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProductJson()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("A service token can read a single product")
    void service_canReadSingleProduct() throws Exception {
        mockMvc.perform(get("/api/v1/products/p1")
                        .header(HttpHeaders.AUTHORIZATION,
                                serviceToken(SERVICE_SECRET, "orders-service", "inventory-service")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A service token cannot list the catalog")
    void service_cannotListProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION,
                                serviceToken(SERVICE_SECRET, "orders-service", "inventory-service")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A service token cannot modify stock")
    void service_cannotRestock() throws Exception {
        mockMvc.perform(patch("/api/v1/products/p1/restock")
                        .header(HttpHeaders.AUTHORIZATION,
                                serviceToken(SERVICE_SECRET, "orders-service", "inventory-service"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A service token signed with the wrong key is rejected")
    void service_wrongKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/products/p1")
                        .header(HttpHeaders.AUTHORIZATION,
                                serviceToken("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz", "orders-service", "inventory-service")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A service token issued for another audience is rejected")
    void service_wrongAudience_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/products/p1")
                        .header(HttpHeaders.AUTHORIZATION,
                                serviceToken(SERVICE_SECRET, "orders-service", "notification-service")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ROLE_SERVICE claimed in a token signed with the user key is rejected")
    void userKey_withServiceRole_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/products/p1")
                        .header(HttpHeaders.AUTHORIZATION, userToken("attacker", "ROLE_SERVICE")))
                .andExpect(status().isUnauthorized());
    }

    private String validProductJson() {
        return """
                {"sku":"SKU-1","name":"Mouse","description":"Wireless mouse",
                 "price":25.00,"initialQuantity":10}
                """;
    }

    private String userToken(String subject, String role) {
        return "Bearer " + Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(USER_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String serviceToken(String secret, String issuer, String audience) {
        return "Bearer " + Jwts.builder()
                .subject(issuer)
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("role", "ROLE_SERVICE")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }


}

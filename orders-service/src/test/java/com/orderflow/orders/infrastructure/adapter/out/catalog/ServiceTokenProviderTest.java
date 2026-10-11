package com.orderflow.orders.infrastructure.adapter.out.catalog;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTokenProviderTest {

    private static final String SECRET = "fedcba9876543210fedcba9876543210";
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("Token carries service role, issuer and audience")
    void token_hasExpectedClaims() {
        ServiceTokenProvider provider = new ServiceTokenProvider(
                SECRET, "orders-service", "inventory-service", Duration.ofMinutes(5));

        Claims claims = Jwts.parser().verifyWith(key)
                .requireIssuer("orders-service")
                .requireAudience("inventory-service")
                .build()
                .parseSignedClaims(provider.getToken())
                .getPayload();

        assertThat(claims.get("role", String.class)).isEqualTo("ROLE_SERVICE");
        assertThat(claims.getSubject()).isEqualTo("orders-service");
    }

    @Test
    @DisplayName("Token is cached while it is far from expiring")
    void token_isCached() {
        ServiceTokenProvider provider = new ServiceTokenProvider(
                SECRET, "orders-service", "inventory-service", Duration.ofMinutes(5));

        assertThat(provider.getToken()).isEqualTo(provider.getToken());
    }

    @Test
    @DisplayName("Token is renewed when it is inside the refresh margin")
    void token_isRenewedNearExpiry() {
        ServiceTokenProvider provider = new ServiceTokenProvider(
                SECRET, "orders-service", "inventory-service", Duration.ofSeconds(10));

        assertThat(provider.getToken()).isNotEqualTo(provider.getToken());
    }


}

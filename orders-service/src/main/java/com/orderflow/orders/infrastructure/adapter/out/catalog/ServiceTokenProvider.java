package com.orderflow.orders.infrastructure.adapter.out.catalog;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues short-lived JWTs that identify orders-service when calling other services.
 * The token is cached and renewed shortly before it expires.
 */
@Component
public class ServiceTokenProvider {

    private static final String SERVICE_ROLE = "ROLE_SERVICE";
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(30);

    private final SecretKey key;
    private final String issuer;
    private final String audience;
    private final Duration ttl;

    private volatile CachedToken cached;

    @Autowired
    public ServiceTokenProvider(
            @Value("${service-auth.secret}") String secret,
            @Value("${service-auth.issuer:orders-service}") String issuer,
            @Value("${service-auth.audience:inventory-service}") String audience,
            @Value("${service-auth.token-ttl:5m}") Duration ttl
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.ttl = ttl;
    }

    public String getToken() {
        Instant now = Instant.now();
        CachedToken current = cached;
        if (needsRefresh(current, now)) {
            synchronized (this) {
                current = cached;
                if (needsRefresh(current, now)) {
                    current = issue(now);
                    cached = current;
                }
            }
        }
        return current.value();
    }

    private boolean needsRefresh(CachedToken token, Instant now) {
        return token == null || now.isAfter(token.expiresAt().minus(REFRESH_MARGIN));
    }

    private CachedToken issue(Instant now) {
        Instant expiresAt = now.plus(ttl);
        String jwt = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(issuer)
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("role", SERVICE_ROLE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new CachedToken(jwt, expiresAt);
    }

    private record CachedToken(String value, Instant expiresAt) {}


}
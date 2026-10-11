package com.orderflow.inventory.infrastructure.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;


@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> USER_ROLES = Set.of("ROLE_USER", "ROLE_ADMIN");
    private static final String SERVICE_ROLE = "ROLE_SERVICE";

    private final SecretKey userKey;
    private final SecretKey serviceKey;
    private final String serviceIssuer;
    private final String serviceAudience;

    public JwtAuthenticationFilter(
            @Value("${jwt.secret}") String userSecret,
            @Value("${service-auth.secret}") String serviceSecret,
            @Value("${service-auth.issuer:orders-service}") String serviceIssuer,
            @Value("${service-auth.audience:inventory-service}") String serviceAudience
    ) {
        this.userKey = Keys.hmacShaKeyFor(userSecret.getBytes(StandardCharsets.UTF_8));
        this.serviceKey = Keys.hmacShaKeyFor(serviceSecret.getBytes(StandardCharsets.UTF_8));
        this.serviceIssuer = serviceIssuer;
        this.serviceAudience = serviceAudience;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            authenticateAsUser(token)
                    .or(() -> authenticateAsService(token))
                    .ifPresentOrElse(
                            auth -> SecurityContextHolder.getContext().setAuthentication(auth),
                            () -> {
                                SecurityContextHolder.clearContext();
                                log.warn("Rejected invalid bearer token");
                            });
        }

        filterChain.doFilter(request, response);
    }

    private Optional<Authentication> authenticateAsUser(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(userKey).build()
                    .parseSignedClaims(token).getPayload();
            String role = claims.get("role", String.class);
            if (!StringUtils.hasText(claims.getSubject()) || !USER_ROLES.contains(role)) {
                return Optional.empty();
            }
            return Optional.of(toAuthentication(claims.getSubject(), role));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Optional<Authentication> authenticateAsService(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(serviceKey)
                    .requireIssuer(serviceIssuer)
                    .requireAudience(serviceAudience)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String role = claims.get("role", String.class);
            if (!StringUtils.hasText(claims.getSubject()) || !SERVICE_ROLE.equals(role)) {
                return Optional.empty();
            }
            return Optional.of(toAuthentication(claims.getSubject(), role));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Authentication toAuthentication(String principal, String role) {
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role)));
    }
}

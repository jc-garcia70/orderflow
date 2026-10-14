package com.orderflow.orders.infrastructure.config;

import com.orderflow.orders.infrastructure.adapter.out.catalog.InventoryResilience;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class InventoryResilienceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(InventoryResilienceConfiguration.class);

    @Bean
    public CircuitBreaker inventoryCircuitBreaker(
            @Value("${inventory-service.resilience.circuit-breaker.window-size:10}") int windowSize,
            @Value("${inventory-service.resilience.circuit-breaker.minimum-calls:5}") int minimumCalls,
            @Value("${inventory-service.resilience.circuit-breaker.failure-rate-threshold:50}") float failureRate,
            @Value("${inventory-service.resilience.circuit-breaker.open-state-wait:10s}") Duration openWait
    ) {
        CircuitBreaker circuitBreaker = CircuitBreaker.of("inventory-service",
                InventoryResilience.circuitBreakerConfig(windowSize, minimumCalls, failureRate, openWait));
        circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.warn("Inventory circuit breaker: {}", event.getStateTransition()));
        return circuitBreaker;
    }

    @Bean
    public Retry inventoryRetry(
            @Value("${inventory-service.resilience.retry.max-attempts:3}") int maxAttempts,
            @Value("${inventory-service.resilience.retry.initial-backoff-ms:200}") long initialBackoffMs
    ) {
        return Retry.of("inventory-service", InventoryResilience.retryConfig(
                maxAttempts, IntervalFunction.ofExponentialRandomBackoff(initialBackoffMs, 2.0, 0.5)));
    }


}

package com.orderflow.orders.infrastructure.adapter.out.catalog;

import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.orders.application.exception.InventoryAccessDeniedException;
import com.orderflow.orders.application.exception.InventoryServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;

import java.time.Duration;

/** Resilience rules for calls from orders-service to inventory-service. */
public final class InventoryResilience {

    private InventoryResilience(){}

    public static CircuitBreakerConfig circuitBreakerConfig(
            int windowSize, int minimumCalls, float failureRateThreshold, Duration openStateWait) {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(windowSize)
                .minimumNumberOfCalls(minimumCalls)
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(openStateWait)
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordException(ex -> ex instanceof InventoryServiceUnavailableException)
                .ignoreExceptions(ResourceNotFoundException.class)
                .build();
    }

    /** Retries transient failures only. Rejected credentials are not transient. */
    public static RetryConfig retryConfig(int maxAttempts, IntervalFunction interval) {
        return RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(interval)
                .retryOnException(ex -> ex instanceof InventoryServiceUnavailableException
                        && !(ex instanceof InventoryAccessDeniedException))
                .build();
    }

}

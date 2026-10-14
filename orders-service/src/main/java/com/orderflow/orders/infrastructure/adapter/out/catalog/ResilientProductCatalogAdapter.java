package com.orderflow.orders.infrastructure.adapter.out.catalog;

import com.orderflow.orders.application.exception.InventoryServiceUnavailableException;
import com.orderflow.orders.application.port.out.ProductCatalogPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import io.github.resilience4j.retry.Retry;

import java.util.function.Supplier;

/**
 * Wraps the HTTP adapter with Retry(CircuitBreaker(call)): each attempt is counted by the
 * breaker, and once the circuit is open the retry stops and callers fail fast.
 */
@Component
@Primary
public class ResilientProductCatalogAdapter implements ProductCatalogPort {

    private final InventoryProductRestAdapter delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ResilientProductCatalogAdapter(InventoryProductRestAdapter delegate,
                 CircuitBreaker inventoryCircuitBreaker, Retry inventoryRetry) {
        this.delegate = delegate;
        this.circuitBreaker = inventoryCircuitBreaker;
        this.retry = inventoryRetry;
    }

    @Override
    public ProductDetails getById(String productId) {

        Supplier<ProductDetails> guarded =
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> delegate.getById(productId));

        Supplier<ProductDetails> withRetry = Retry.decorateSupplier(retry,guarded);
        try{
            return withRetry.get();
        } catch (CallNotPermittedException ex) {
            throw new InventoryServiceUnavailableException("Inventory service is temporarily unavailable", ex);
        }

    }


}

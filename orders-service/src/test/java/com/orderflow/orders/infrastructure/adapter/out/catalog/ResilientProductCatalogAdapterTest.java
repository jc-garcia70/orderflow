package com.orderflow.orders.infrastructure.adapter.out.catalog;

import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.orders.application.exception.InventoryAccessDeniedException;
import com.orderflow.orders.application.exception.InventoryServiceUnavailableException;
import com.orderflow.orders.application.port.out.ProductCatalogPort.ProductDetails;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientProductCatalogAdapterTest {

    private static final ProductDetails PRODUCT = new ProductDetails(new BigDecimal("10.00"), true);

    @Mock
    private InventoryProductRestAdapter delegate;

    private CircuitBreaker circuitBreaker;
    private ResilientProductCatalogAdapter adapter;

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.of("test",
                InventoryResilience.circuitBreakerConfig(4, 4, 50f, Duration.ofSeconds(30)));
        Retry retry = Retry.of("test", InventoryResilience.retryConfig(
                3, IntervalFunction.of(Duration.ofMillis(1))));
        adapter = new ResilientProductCatalogAdapter(delegate, circuitBreaker, retry);
    }

    private static InventoryServiceUnavailableException unavailable() {
        return new InventoryServiceUnavailableException("down", new RuntimeException());
    }

    @Test
    @DisplayName("A transient failure is retried and the call succeeds")
    void retriesTransientFailure() {
        when(delegate.getById("p1")).thenThrow(unavailable()).thenReturn(PRODUCT);

        assertThat(adapter.getById("p1")).isEqualTo(PRODUCT);

        verify(delegate, times(2)).getById("p1");
    }

    @Test
    @DisplayName("A 404 is neither retried nor counted as a failure")
    void notFoundIsNotRetriedNorCounted() {
        when(delegate.getById("p1")).thenThrow(new ResourceNotFoundException("Product", "p1"));

        assertThatThrownBy(() -> adapter.getById("p1")).isInstanceOf(ResourceNotFoundException.class);

        verify(delegate, times(1)).getById("p1");
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    @DisplayName("Rejected service credentials are not retried")
    void accessDeniedIsNotRetried() {
        when(delegate.getById("p1")).thenThrow(
                new InventoryAccessDeniedException("denied", new RuntimeException()));

        assertThatThrownBy(() -> adapter.getById("p1"))
                .isInstanceOf(InventoryAccessDeniedException.class);

        verify(delegate, times(1)).getById("p1");
    }

    @Test
    @DisplayName("Retries are exhausted and the last failure is propagated")
    void retriesAreExhausted() {
        when(delegate.getById("p1")).thenThrow(unavailable());

        assertThatThrownBy(() -> adapter.getById("p1"))
                .isInstanceOf(InventoryServiceUnavailableException.class);

        verify(delegate, times(3)).getById("p1");
    }

    @Test
    @DisplayName("Once the circuit is open, calls fail fast without reaching inventory")
    void openCircuitFailsFast() {
        when(delegate.getById("p1")).thenThrow(unavailable());

        // Call 1 uses 3 attempts. In call 2 the 4th failure fills the window and opens the circuit.
        assertThatThrownBy(() -> adapter.getById("p1")).isInstanceOf(InventoryServiceUnavailableException.class);
        assertThatThrownBy(() -> adapter.getById("p1")).isInstanceOf(InventoryServiceUnavailableException.class);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        verify(delegate, times(4)).getById("p1");

        assertThatThrownBy(() -> adapter.getById("p1"))
                .isInstanceOf(InventoryServiceUnavailableException.class)
                .hasMessageContaining("temporarily unavailable");

        verify(delegate, times(4)).getById("p1");   // no new call reached inventory
    }
}
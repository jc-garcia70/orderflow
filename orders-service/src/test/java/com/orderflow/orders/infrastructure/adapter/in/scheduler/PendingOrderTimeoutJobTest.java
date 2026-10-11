package com.orderflow.orders.infrastructure.adapter.in.scheduler;

import com.orderflow.orders.application.port.in.OrderTimeoutUseCase;
import com.orderflow.orders.infrastructure.adapter.in.sheduler.PendingOrderTimeoutJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingOrderTimeoutJobTest {

    @Mock
    private OrderTimeoutUseCase useCase;

    @Test
    @DisplayName("A failure on one order doesn't prevent cancelling the rest")
    void cancelStaleOrders_ContinuesAfterFailure() {
        when(useCase.findStalePendingOrderIds(Duration.ofMinutes(15), 100))
                .thenReturn(List.of("a", "b"));
        doThrow(new IllegalStateException("boom")).when(useCase).cancelIfStillPending("a");

        new PendingOrderTimeoutJob(useCase, Duration.ofMinutes(15), 100).cancelStaleOrders();

        verify(useCase).cancelIfStillPending("a");
        verify(useCase).cancelIfStillPending("b");
    }

    @Test
    @DisplayName("Nothing happens when there are no stale orders")
    void cancelStaleOrders_NoStaleOrders() {
        when(useCase.findStalePendingOrderIds(Duration.ofMinutes(15), 100)).thenReturn(List.of());

        new PendingOrderTimeoutJob(useCase, Duration.ofMinutes(15), 100).cancelStaleOrders();

        verify(useCase, never()).cancelIfStillPending(anyString());
    }
}
package com.orderflow.orders.infrastructure.adapter.in.sheduler;

import com.orderflow.orders.application.port.in.OrderTimeoutUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Cancels orders stuck in PENDING (e.g. the inventory event ended up in a DLT).
 * Each order is cancelled in its own transaction via the use case proxy.
 */
@Component
public class PendingOrderTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderTimeoutJob.class);

    private final OrderTimeoutUseCase orderTimeoutUseCase;
    private final Duration timeout;
    private final int batchSize;

    public PendingOrderTimeoutJob(
            OrderTimeoutUseCase orderTimeoutUseCase,
            @Value("${orders.pending-timeout:15m}") Duration timeout,
            @Value("${orders.timeout-batch-size:100}") int batchSize
    ) {
        this.orderTimeoutUseCase = orderTimeoutUseCase;
        this.timeout = timeout;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${orders.timeout-check-interval-ms:60000}")
    public void cancelStaleOrders() {
        List<String> staleIds = orderTimeoutUseCase.findStalePendingOrderIds(timeout, batchSize);

        if (staleIds.isEmpty()) {
            return;
        }

        log.info("Found {} stale PENDING orders (older than {})", staleIds.size(), timeout);

        for (String orderId : staleIds) {

            try {
                orderTimeoutUseCase.cancelIfStillPending(orderId);
            } catch (Exception ex) {
                log.error("Failed to cancel stale order '{}'", orderId, ex);
            }

        }

    }

}

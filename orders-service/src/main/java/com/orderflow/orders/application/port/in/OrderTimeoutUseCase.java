package com.orderflow.orders.application.port.in;

import java.time.Duration;
import java.util.List;

/**
 * Inbound port for cancelling orders whose saga never completed.
 */
public interface OrderTimeoutUseCase {

    /** IDs of PENDING orders older than {@code timeout}, oldest first. */
    List<String> findStalePendingOrderIds(Duration timeout, int limit);

    /**
     * Cancels the order with reason ORDER_TIMEOUT only if it is still PENDING.
     * Runs in its own transaction, so one failure doesn't affect other orders.
     */
    void cancelIfStillPending(String orderId);

}

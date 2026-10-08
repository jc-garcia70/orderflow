package com.orderflow.inventory.application.port.out;

/**
 * Records whether an order's initial stock reservation has already been processed.
 */
public interface ProcessedOrderReservationPort {

    boolean existsByOrderId(String orderId);

    void markProcessed(String orderId);
}

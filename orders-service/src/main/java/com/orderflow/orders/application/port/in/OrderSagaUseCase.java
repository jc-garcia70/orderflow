package com.orderflow.orders.application.port.in;

/**
 * Inbound port handling asynchronous saga responses from inventory-service
 */
public interface OrderSagaUseCase {

    /**
     * Confirms the order when inventory reservation succeeds.
     * Transitions status to CONFIRMED and publishes OrderConfirmedEvent.
     */
    void handleStockReserved(String orderId);


    /**
     * Cancels the order when inventory reservation fails (Saga compensation).
     * Transitions status to CANCELLED and publishes OrderCancelledEvent.
     */
    void handleStockRejected(String orderId, String reason);

}

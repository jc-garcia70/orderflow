package com.orderflow.inventory.application.port.in;


import java.util.List;

/**
 * Inbound port orchestrating stock reservation, compensation, and confirmation for orders.
 */
public interface StockReservationUseCase {

    record OrderItemRequest(
            String productId,
            int quantity
    ){}

    record ReserveStockCommand(
            String orderId,
            List<OrderItemRequest> items
    ){}

    record ReleaseStockCommand(
            String orderId
    ){}

    record ConfirmStockCommand(
            String orderId
    ) {}

    /**
     * Executes atomic All-or-Nothing stock reservation for an order.
     * Publishes StockReservedPayload on success, or StockRejectedPayload on failure.
     */
    void reserveStock(ReserveStockCommand command);

    /**
     * Releases reserved stock back to available pool during saga compensation.
     */
    void  releaseStock(ReleaseStockCommand command);

    /**
     * Permanently commits reserved stock when an order is finalized.
     */
    void confirmStock(ConfirmStockCommand command);


}

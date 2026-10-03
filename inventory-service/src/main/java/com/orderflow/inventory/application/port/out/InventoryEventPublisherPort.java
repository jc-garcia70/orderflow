package com.orderflow.inventory.application.port.out;

import com.orderflow.common.event.inventory.StockRejectedPayload;
import com.orderflow.common.event.inventory.StockReservedPayload;

/**
 * Outbound port for publishing inventory domain events to the Kafka event bus.
 */
public interface InventoryEventPublisherPort {

    /**
     * Publishes an event notifying that inventory was a successfully reserved for an order.
     */
    void publishStockReserved(StockReservedPayload payload);


    /**
     * Publishes an event notifying that stock reservation was rejected (triggers Saga compensation)
     */
    void publishStockRejected(StockRejectedPayload payload);


}

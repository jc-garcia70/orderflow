package com.orderflow.common.event;

/**
 * Constants for Kafka topic names across OrderFlow microservices.
 */
public final class KafkaTopics {

    private KafkaTopics() {
        // Prevent instantiation
    }

    /**
     * Topic for order lifecycle events (OrderCreated, OrderConfirmed, OrderCancelled).
     * Produced by orders-service.
     */
    public static final String ORDER_EVENTS = "order-events";

    /**
     * Topic for inventory reservation events (StockReserved, StockRejected).
     * Produced by inventory-service.
     */
    public static final String INVENTORY_EVENTS = "inventory-events";
}

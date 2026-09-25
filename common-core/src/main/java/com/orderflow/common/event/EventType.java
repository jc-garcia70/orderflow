package com.orderflow.common.event;

/**
 * Standard event type identifiers used in EventEnvelope headers and routing.
 */
public final class EventType {

    private EventType() {
        // Prevent instantiation
    }

    public static final String ORDER_CREATED = "OrderCreatedEvent";
    public static final String STOCK_RESERVED = "StockReservedEvent";
    public static final String STOCK_REJECTED = "StockRejectedEvent";
    public static final String ORDER_CONFIRMED = "OrderConfirmedEvent";
    public static final String ORDER_CANCELLED = "OrderCancelledEvent";
}

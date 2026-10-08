package com.orderflow.orders.application.port.out;

import com.orderflow.common.event.order.OrderCancelledPayload;
import com.orderflow.common.event.order.OrderConfirmedPayload;
import com.orderflow.common.event.order.OrderCreatedPayload;

/**
 * Outbound port for publishing order domain events to Kafka topics.
 */
public interface OrderEventPublisherPort {

    void publishOrderCreated(OrderCreatedPayload payload);

    void publishOrderConfirmed(OrderConfirmedPayload payload);

    void publishOrderCancelled(OrderCancelledPayload payload);

}

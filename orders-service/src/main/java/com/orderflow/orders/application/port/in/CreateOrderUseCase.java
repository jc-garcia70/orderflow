package com.orderflow.orders.application.port.in;

import com.orderflow.orders.domain.model.Order;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inbound port defining the order placement use case.
 */
public interface CreateOrderUseCase {

    record OrderItemCommand(
            String productId,
            int quantity,
            BigDecimal unitPrice
    ){}

    record CreateOrderCommand(
            String userId,
            List<OrderItemCommand> items
    ){}

    /**
     * Creates a new order in PENDING status and initiates the stock reservation Saga.
     */
    Order createOrder(CreateOrderCommand command);

}

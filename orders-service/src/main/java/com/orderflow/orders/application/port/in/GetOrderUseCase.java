package com.orderflow.orders.application.port.in;

import com.orderflow.orders.domain.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Inbound port for querying orders.
 */
public interface GetOrderUseCase {

    Order getOrderById(String id);

    Page<Order> getOrdersByUserId(String userId, Pageable pageable);

    Page<Order> getAllOrders(Pageable pageable);

}

package com.orderflow.orders.application.port.out;

import com.orderflow.orders.domain.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface OrderRepositoryPort {

    Order save(Order order);

    Optional<Order> findById(String id);

    Page<Order> findByUserId(String userId, Pageable pageable);

    Page<Order> findAll(Pageable pageable);

}

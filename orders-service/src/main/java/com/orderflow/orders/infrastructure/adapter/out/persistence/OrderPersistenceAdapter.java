package com.orderflow.orders.infrastructure.adapter.out.persistence;

import com.orderflow.orders.application.port.out.OrderRepositoryPort;
import com.orderflow.orders.domain.model.Order;
import com.orderflow.orders.domain.model.OrderItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary adapter implementing OrderRepositoryPort using Spring Data JPA.
 */
@Component
public class OrderPersistenceAdapter implements OrderRepositoryPort {

    private final SpringDataOrderRepository repository;

    public OrderPersistenceAdapter(SpringDataOrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public Order save(Order order) {

        String orderId = order.getId() != null ? order.getId(): UUID.randomUUID().toString();

        OrderJpaEntity orderEntity = new OrderJpaEntity();
        orderEntity.setId(orderId);
        orderEntity.setUserId(order.getUserId());
        orderEntity.setStatus(order.getStatus());
        orderEntity.setTotalAmount(order.getTotalAmount());
        orderEntity.setCancellationReason(order.getCancellationReason());
        orderEntity.setCreatedAt(order.getCreatedAt());
        orderEntity.setUpdatedAt(order.getUpdatedAt());

        List<OrderItemJpaEntity> itemEntities = new ArrayList<>();

        for(OrderItem item : order.getItems()) {

            String itemId = item.getId() != null ? item.getId() : UUID.randomUUID().toString();

            OrderItemJpaEntity itemEntity = new OrderItemJpaEntity(
                    itemId,
                    orderEntity,
                    item.getProductId(),
                    item.getQuantity(),
                    item.getUnitPrice()

            );
            itemEntities.add(itemEntity);
        }
        orderEntity.setItems(itemEntities);

        OrderJpaEntity savedEntity = repository.save(orderEntity);
        return toDomain(savedEntity);
    }


    @Override
    public Optional<Order> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Page<Order> findByUserId(String userId, Pageable pageable) {
        return repository.findByUserId(userId,pageable).map(this::toDomain);
    }

    @Override
    public Page<Order> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toDomain);
    }

    private Order toDomain(OrderJpaEntity entity) {

        List<OrderItem> domainItems = entity.getItems().stream()
                .map(itemEntity -> new OrderItem(
                        itemEntity.getId(),
                        itemEntity.getProductId(),
                        itemEntity.getQuantity(),
                        itemEntity.getUnitPrice()
                ))
                .toList();

        return new Order(
                entity.getId(),
                entity.getUserId(),
                entity.getStatus(),
                domainItems,
                entity.getTotalAmount(),
                entity.getCancellationReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

}

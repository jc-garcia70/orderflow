package com.orderflow.orders.infrastructure.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for order database queries.
 */
@Repository
public interface SpringDataOrderRepository extends JpaRepository<OrderJpaEntity, String> {

    Page<OrderJpaEntity> findByUserId(String userId, Pageable pageable);

}

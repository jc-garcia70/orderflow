package com.orderflow.orders.infrastructure.adapter.out.persistence;

import com.orderflow.common.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for order database queries.
 */
@Repository
public interface SpringDataOrderRepository extends JpaRepository<OrderJpaEntity, String> {

    Page<OrderJpaEntity> findByUserId(String userId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderJpaEntity o where o.id = :id")
    Optional<OrderJpaEntity> findByIdForUpdate(@Param("id") String id);

    @Query("""
        select o.id from OrderJpaEntity o
        where o.status = :status and o.createdAt < :cutoff
        order by o.createdAt asc
        """)
    List<String> findIdsByStatusAndCreatedAtBefore(
            @Param("status") OrderStatus status,
            @Param("cutoff") Instant cutoff,
            Pageable pageable);

}

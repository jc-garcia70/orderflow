package com.orderflow.orders.infrastructure.adapter.out.messaging.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface OrderOutboxRepository extends JpaRepository<OrderOutboxEntity, String> {

    List<OrderOutboxEntity> findTop100ByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Instant now
    );

    @Transactional
    long deleteByPublishedAtBefore(Instant cutoff);
}

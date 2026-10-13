package com.orderflow.inventory.infrastructure.adapter.out.messaging.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface InventoryOutboxRepository extends JpaRepository<InventoryOutboxEntity, String> {

    /**
     * Locks the returned rows with FOR UPDATE SKIP LOCKED (lock.timeout = -2), so concurrent
     * relay instances never pick the same events. Must run inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<InventoryOutboxEntity> findByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Instant now, Pageable pageable);

    @Transactional
    long deleteByPublishedAtBefore(Instant cutoff);

}

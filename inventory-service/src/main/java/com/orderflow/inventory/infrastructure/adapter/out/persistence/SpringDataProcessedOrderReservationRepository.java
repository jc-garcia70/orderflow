package com.orderflow.inventory.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataProcessedOrderReservationRepository
        extends JpaRepository<ProcessedOrderReservationEntity, String> {
}

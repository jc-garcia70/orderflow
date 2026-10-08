package com.orderflow.inventory.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "processed_order_reservations")
public class ProcessedOrderReservationEntity {

    @Id
    @Column(name = "order_id", nullable = false, updatable = false, length = 36)
    private String orderId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedOrderReservationEntity() {
    }

    public ProcessedOrderReservationEntity(String orderId, Instant processedAt) {
        this.orderId = orderId;
        this.processedAt = processedAt;
    }

    public String getOrderId() {
        return orderId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}

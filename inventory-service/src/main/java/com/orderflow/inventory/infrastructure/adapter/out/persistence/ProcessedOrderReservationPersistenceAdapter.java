package com.orderflow.inventory.infrastructure.adapter.out.persistence;

import com.orderflow.inventory.application.port.out.ProcessedOrderReservationPort;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ProcessedOrderReservationPersistenceAdapter
        implements ProcessedOrderReservationPort {

    private final SpringDataProcessedOrderReservationRepository repository;

    public ProcessedOrderReservationPersistenceAdapter(
            SpringDataProcessedOrderReservationRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public boolean existsByOrderId(String orderId) {
        return repository.existsById(orderId);
    }

    @Override
    public void markProcessed(String orderId) {
        repository.saveAndFlush(new ProcessedOrderReservationEntity(orderId, Instant.now()));
    }
}

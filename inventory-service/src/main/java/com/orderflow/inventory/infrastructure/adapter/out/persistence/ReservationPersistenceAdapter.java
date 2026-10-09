package com.orderflow.inventory.infrastructure.adapter.out.persistence;

import com.orderflow.inventory.application.port.out.ReservationRepositoryPort;
import com.orderflow.inventory.domain.model.Reservation;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ReservationPersistenceAdapter implements ReservationRepositoryPort {

    private final SpringDataReservationRepository repository;

    public ReservationPersistenceAdapter(SpringDataReservationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Reservation save(Reservation reservation) {
        ReservationJpaEntity entity = repository.findById(reservation.getOrderId())
                .orElseGet( () -> {
                   ReservationJpaEntity e = new ReservationJpaEntity();
                   e.setOrderId(reservation.getOrderId());
                   e.setCreatedAt(Instant.now());
                   return e;
                });

        entity.setStatus(reservation.getStatus());
        entity.setUpdatedAt(Instant.now());
        entity.setItems(reservation.getItems().stream()
                .map(item -> new ReservationItemEmbeddable(item.productId(),item.quantity()))
                .collect(Collectors.toCollection(ArrayList::new)));

        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<Reservation> findByOrderId(String orderId) {
        return repository.findById(orderId).map(this::toDomain);
    }

    private Reservation toDomain(ReservationJpaEntity e) {
        return new Reservation(
                e.getOrderId(),
                e.getItems().stream()
                        .map(i -> new Reservation.Item(i.getProductId(), i.getQuantity()))
                        .toList(),
                e.getStatus(),
                e.getVersion());
    }

}

package com.orderflow.inventory.application.port.out;

import com.orderflow.inventory.domain.model.Reservation;

import java.util.Optional;

public interface ReservationRepositoryPort {

    Reservation save(Reservation reservation);
    Optional<Reservation> findByOrderId(String orderId);


}

package com.orderflow.inventory.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationTest {

    @Test
    void confirmTransitionsReservedReservationToConfirmed() {
        Reservation reservation = Reservation.reserved("ord-1", List.of(new Reservation.Item("prod-1", 2)));

        assertThat(reservation.confirm()).isTrue();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void releaseTransitionsReservedReservationToReleased() {
        Reservation reservation = Reservation.reserved("ord-1", List.of(new Reservation.Item("prod-1", 2)));

        assertThat(reservation.release()).isTrue();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void confirmDoesNotTransitionReleasedReservation() {
        Reservation reservation = Reservation.reserved("ord-1", List.of());
        reservation.release();

        assertThat(reservation.confirm()).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void releaseDoesNotTransitionConfirmedReservation() {
        Reservation reservation = Reservation.reserved("ord-1", List.of());
        reservation.confirm();

        assertThat(reservation.release()).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void reservationDoesNotConfirmOrReleaseMoreThanOnce() {
        Reservation confirmed = Reservation.reserved("ord-confirmed", List.of());
        assertThat(confirmed.confirm()).isTrue();
        assertThat(confirmed.confirm()).isFalse();

        Reservation released = Reservation.reserved("ord-released", List.of());
        assertThat(released.release()).isTrue();
        assertThat(released.release()).isFalse();
    }
}

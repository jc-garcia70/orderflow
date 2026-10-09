package com.orderflow.inventory.infrastructure.adapter.out.persistence;

import com.orderflow.inventory.domain.model.ReservationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


@Getter@Setter@NoArgsConstructor@AllArgsConstructor
@Entity
@Table(name = "stock_reservations")
public class ReservationJpaEntity {

    @Id
    @Column(name = "order_id", nullable = false, updatable = false, length = 36)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "stock_reservation_items",
            joinColumns = @JoinColumn(name = "order_id"))
    private List<ReservationItemEmbeddable> items = new ArrayList<>();

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

}

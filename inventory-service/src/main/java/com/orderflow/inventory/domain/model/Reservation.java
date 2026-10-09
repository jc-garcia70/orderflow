package com.orderflow.inventory.domain.model;

import java.util.List;

public class Reservation{

    public record Item(String productId, int quantity){}

    private final String orderId;
    private final List<Item> items;
    private ReservationStatus status;
    private Long version;

    public Reservation(String orderId, List<Item> items, ReservationStatus status, Long version) {
        this.orderId = orderId;
        this.items = List.copyOf(items);
        this.status = status;
        this.version = version;
    }

    public static Reservation reserved(String orderId, List<Item> items) {
        return new Reservation(orderId, items, ReservationStatus.RESERVED, null);
    }

    /** @return true if the transition happened; false if it was a duplicate or no longer applicable. */
    public boolean confirm() {
        if (status != ReservationStatus.RESERVED) return false;
        status = ReservationStatus.CONFIRMED;
        return true;
    }

    public boolean release(){
        if(status != ReservationStatus.RESERVED) return false;
        status = ReservationStatus.RELEASED;
        return true;
    }


    public String getOrderId() { return orderId; }
    public List<Item> getItems() { return items; }
    public ReservationStatus getStatus() { return status; }
    public Long getVersion() { return version; }


}

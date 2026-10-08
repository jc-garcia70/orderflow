package com.orderflow.orders.domain.model;

import com.orderflow.common.enums.OrderStatus;
import com.orderflow.common.exception.BusinessException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Pure domain aggregate root representing an Order and its lifecycle.
 * Encapsulates status transitions for the Choreographed Saga pattern without frameworks dependencies.
 */
public class Order {

    private String id;
    private String userId;
    private OrderStatus status;
    private List<OrderItem> items;
    private BigDecimal totalAmount;
    private String cancellationReason;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Reconstitution constructor for persistence adapters.
     */
    public Order(String id, String userId, OrderStatus status, List<OrderItem> items,
                 BigDecimal totalAmount, String cancellationReason, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.status = status != null ? status : OrderStatus.PENDING;
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.totalAmount = totalAmount;
        this.cancellationReason = cancellationReason;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
        validateInvariants();
    }

    /**
     * Creation constructor for brand new orders placed by clients.
     */
    public Order(String userId, List<OrderItem> items) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException("User ID cannot be null or blank");
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException("Order must contain at least one item");
        }
        this.id = null;
        this.userId = userId;
        this.status = OrderStatus.PENDING;
        this.items = new ArrayList<>(items);
        this.totalAmount = calculateTotalAmount(items);
        this.cancellationReason = null;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }


    /**
     * Confirms the order after successful stock reservation in inventory-service
     * Transitions state from PENDING to CONFIRMED
     */
    public void confirm() {

        if(this.status != OrderStatus.PENDING) {
            throw new BusinessException(String.format(
                    "Cannot confirm order '%s' with current status '%s'. Only PENDING orders can be confirmed.",
                    this.id, this.status));
        }
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = Instant.now();

    }

    /**
     * Cancels the order during Saga compensation (e.g. Insufficient stock rejection).
     * Transitions state from PENDING to CANCELLED
     */
    public void cancel(String reason){

        if (this.status != OrderStatus.PENDING) {
            throw new BusinessException(String.format(
                    "Cannot cancel order '%s' with current status '%s'. Only PENDING orders can be cancelled.",
                    this.id, this.status));
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Cancellation reason cannot be null or blank");
        }
        this.status = OrderStatus.CANCELLED;
        this.cancellationReason = reason;
        this.updatedAt = Instant.now();

    }

    private BigDecimal calculateTotalAmount(List<OrderItem> items) {
        return items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validateInvariants() {

        if (this.userId == null || this.userId.isBlank()) {
            throw new BusinessException("Order userId cannot be null or blank");
        }

        if (this.items.isEmpty()) {
            throw new BusinessException("Order must contain at least one item");
        }

        if (this.totalAmount == null || this.totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Order totalAmount must be strictly greater than zero");
        }

    }

    // Getters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order order)) return false;
        return Objects.equals(id, order.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}

package com.orderflow.inventory.domain.model;


import com.orderflow.common.exception.BusinessException;
import com.orderflow.common.exception.InsufficientStockException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Pure domain aggregate representing a Product and its inventory stock.
 * Encapsule core business invariants for stock reservation, compensation, and replenishment
 * without frameworks dependencies.
 */
public class Product {

    private String id;
    private String sku; // Reference
    private String name;
    private String description;
    private BigDecimal price;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private boolean active;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;


    /**
     * Full constructor for reconstitution from persistence or testing.
     */
    public Product(String id, String sku, String name, String description, BigDecimal price,
            Integer availableQuantity, Integer reservedQuantity, boolean active,
            Long version, Instant createdAt, Instant updatedAt
    ) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.price = price;
        this.availableQuantity = availableQuantity != null ? availableQuantity : 0;
        this.reservedQuantity = reservedQuantity != null ? reservedQuantity : 0;
        this.active = active;
        this.version = version;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
        validateInvariants();
    }

    /**
     * Creation constructor for brand new products.
     */
    public Product(String sku, String name, String description, BigDecimal price,
                   Integer initialQuantity){

        this(null, sku, name, description, price, initialQuantity != null ? initialQuantity : 0,
                0, true, null, Instant.now(),Instant.now());

    }

    // --- Core Domain business logic and state transitions

    /**
     * Reserve stock for and in-flight order.
     * Transitions units form available to reserved.
     *
     * @param quantity the quantity to reserve, must be > 0
     * @throws BusinessException if the product is inactive or quantity <= 0
     * @throws InsufficientStockException if availableQuantity < quantity
     */
    public void reserveStock(int quantity){

        if(!this.active){
            throw new BusinessException(String.format("Product '%s' (%s) is inactive and cannot be" +
                    " reserved", this.sku, this.id));
        }

        if(quantity<=0){
            throw new BusinessException("Reservation quantity must be strictly greater than zero");
        }

        if(this.availableQuantity < quantity){
            throw new InsufficientStockException(this.id != null ? this.id : this.sku, quantity, this.availableQuantity);
        }

        this.availableQuantity -= quantity;
        this.reservedQuantity += quantity;
        this.updatedAt = Instant.now();

    }

    /**
     * Releases previously reserved stock back to available stock.
     * Triggered during saga compensation (e.g., payment failure, order cancellation).
     *
     * @param quantity the quantity to release back to available, must be > 0
     * @throws BusinessException if quantity <= 0 or quantity > reservedQuantity
     */
    public void releaseStock(int quantity){
        if(quantity<=0){
            throw new BusinessException("Release quantity must be strictly greater than zero");
        }
        if(quantity>this.reservedQuantity){
            throw new BusinessException(String.format(
                    "Cannot release %d units of product '%s'. Only %d units are currently reserved",
                    quantity, this.sku, this.reservedQuantity
            ));
        }

        this.reservedQuantity -= quantity;
        this.availableQuantity += quantity;
        this.updatedAt = Instant.now();
    }


    /**
     * Confirms deduction of reserved stock when an order is finalized and confirmed.
     * Stock is permanently removed from the warehouse inventory.
     *
     * @param quantity the quantity to permanently deduct, must be > 0
     * @throws BusinessException if quantity <= 0 or quantity > reservedQuantity
     */
    public void confirmReservation(int quantity){
        if(quantity<= 0){
            throw new BusinessException("Confirmation quantity must be strictly greater than zero");
        }

        if(quantity>this.reservedQuantity){
            throw new BusinessException(String.format(
                    "Cannot confirm %d units of product '%s'. Only %d units are currently reserved",
                    quantity, this.sku, this.reservedQuantity
            ));
        }

        this.reservedQuantity -= quantity;
        this.updatedAt = Instant.now();

    }

    /**
     * Restocks or replenishes available inventory.
     *
     * @param quantity the quantity to add, must be > 0
     * @throws BusinessException if quantity <= 0
     */
    public void restock(int quantity){
        if(quantity<= 0){
            throw new BusinessException("Restock quantity must be strictly greater than zero");
        }

        this.availableQuantity += quantity;
        this.updatedAt = Instant.now();
    }

    /**
     * Updates informational details of the product.
     */
    public void updateDetails(String name, String description, BigDecimal price) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("Product name cannot be empty");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Product price must be greater than zero");
        }
        this.name = name;
        this.description = description;
        this.price = price;
        this.updatedAt = Instant.now();
    }

    /**
     * Activates the product, making it eligible for reservations.
     */
    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }
    /**
     * Deactivates the product, preventing any further reservations.
     */
    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }
    /**
     * Calculates the total physical inventory owned (available + reserved).
     */
    public int getTotalQuantity() {
        return this.availableQuantity + this.reservedQuantity;
    }

    /**
     * Validates domain invariants ensuring quantities never fall below zero.
     */
    private void validateInvariants() {
        if (this.availableQuantity < 0) {
            throw new BusinessException("Available quantity cannot be negative");
        }
        if (this.reservedQuantity < 0) {
            throw new BusinessException("Reserved quantity cannot be negative");
        }
    }


    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public Integer getReservedQuantity() {
        return reservedQuantity;
    }

    public boolean isActive() {
        return active;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
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
        if (!(o instanceof Product product)) return false;
        return Objects.equals(id, product.id) && Objects.equals(sku, product.sku);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sku);
    }

}

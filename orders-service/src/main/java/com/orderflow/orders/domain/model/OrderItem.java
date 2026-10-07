package com.orderflow.orders.domain.model;

import com.orderflow.common.exception.BusinessException;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Domain entity representing an individual item within an order
 */
public class OrderItem {

    private String id;
    private String productId;
    private int quantity;
    private BigDecimal unitPrice;

    public OrderItem(String id, String productId, int quantity, BigDecimal unitPrice){

        if(productId == null || productId.isBlank()){
            throw new BusinessException("Product ID cannot be null or blank");
        }

        if(quantity <= 0){
            throw new BusinessException("Order item quantity must be strictly greater than zero");
        }

        if(unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0){
            throw new BusinessException("Order item unit price must be strictly greater than zero");
        }

        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;

    }

    public OrderItem(String productId, int quantity, BigDecimal unitPrice) {
        this(null, productId, quantity, unitPrice);
    }

    public BigDecimal getSubtotal() {
        return this.unitPrice.multiply(BigDecimal.valueOf(this.quantity));
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderItem item)) return false;
        return Objects.equals(id, item.id) && Objects.equals(productId, item.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, productId);
    }


}

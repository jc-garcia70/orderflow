package com.orderflow.inventory.infrastructure.adapter.in.rest.dto;

import com.orderflow.inventory.domain.model.Product;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        String id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        Integer availableQuantity,
        Integer reservedQuantity,
        Integer totalQuantity,
        boolean active,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductResponse fromDomain(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getAvailableQuantity(),
                product.getReservedQuantity(),
                product.getTotalQuantity(),
                product.isActive(),
                product.getVersion(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
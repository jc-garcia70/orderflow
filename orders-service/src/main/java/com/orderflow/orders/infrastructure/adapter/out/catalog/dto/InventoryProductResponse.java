package com.orderflow.orders.infrastructure.adapter.out.catalog.dto;

import java.math.BigDecimal;

public record InventoryProductResponse(
        BigDecimal price,
        boolean active
) {
}
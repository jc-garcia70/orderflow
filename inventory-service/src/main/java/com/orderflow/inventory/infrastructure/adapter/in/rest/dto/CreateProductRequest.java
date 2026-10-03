package com.orderflow.inventory.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateProductRequest(

        @NotBlank(message = "SKU must not be blank")
        String sku,

        @NotBlank(message = "Name must not be blank")
        String name,

        @NotBlank(message = "Name must not be blank")
        @Size(min = 6, message = "The product description must be at least 6 characters long.")
        String description,

        @NotNull(message = "Price must not be null")
        @DecimalMin(value = "0.01", message = "Price must be strictly greater than zero")
        BigDecimal price,

        @NotNull(message = "Initial quantity must not be null")
        @Min(value = 0, message = "Initial quantity cannot be negative")
        Integer initialQuantity
) {}

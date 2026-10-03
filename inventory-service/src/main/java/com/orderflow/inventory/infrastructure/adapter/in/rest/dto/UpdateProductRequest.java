package com.orderflow.inventory.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateProductRequest(

        @NotBlank(message = "Name must not be blank")
        String name,

        @NotBlank(message = "Name must not be blank")
        @Size(min = 6, message = "The product description must be at least 6 characters long.")
        String description,

        @NotNull(message = "Price must not be null")
        @DecimalMin(value = "0.01", message = "Price must be strictly greater than zero")
        BigDecimal price
) {}

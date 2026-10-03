package com.orderflow.inventory.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RestockProductRequest(

        @NotNull(message = "Quantity must not be null")
        @Min(value = 1, message = "Restock quantity must be at least 1")
        Integer quantity

) {}

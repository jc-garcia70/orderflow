package com.orderflow.orders.infrastructure.adapter.in.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateOrderRequest(
        @NotEmpty(message = "items list must not be empty")
        List<@NotNull @Valid OrderItemRequest> items
) {
    public record OrderItemRequest(
            @NotBlank(message = "productId must not be blank")
            String productId,

            @NotNull(message = "quantity must not be null")
            @Positive(message = "quantity must be greater than zero")
            Integer quantity
    ) {
    }
}


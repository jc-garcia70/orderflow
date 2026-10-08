package com.orderflow.orders.infrastructure.adapter.in.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(

        @NotBlank(message = "userId  must not be blank")
        String userId,

        @NotEmpty(message = "items list must not be empty")
        @Valid
        List<OrderItemRequest> items
) {

    public record OrderItemRequest(

            @NotBlank(message = "productId must not be blank")
            String productId,

            @NotNull(message = "quantity must not be null")
            Integer quantity,

            @NotNull(message = "unitPrice must not be null")
            BigDecimal unitPrice

    ){
    }

}


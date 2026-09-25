package com.orderflow.common.event.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payload published when a new order is initially placed in PENDIENTE status.
 */
public record OrderCreatedPayload(
        @JsonProperty("orderId")
        @NotBlank(message = "orderId must not be blank")
        String orderId,

        @JsonProperty("userId")
        @NotBlank(message = "userId must not be blank")
        String userId,

        @JsonProperty("items")
        @NotEmpty(message = "items list must not be empty")
        @Valid
        List<OrderItemDto> items,

        @JsonProperty("totalAmount")
        @NotNull(message = "totalAmount must not be null")
        @DecimalMin(value = "0.01", message = "totalAmount must be greater than 0")
        BigDecimal totalAmount
) {
}

package com.orderflow.common.event.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.orderflow.common.enums.OrderStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Payload published when an order is successfully confirmed after stock reservation.
 */
public record OrderConfirmedPayload(
        @JsonProperty("orderId")
        @NotBlank(message = "orderId must not be blank")
        String orderId,

        @JsonProperty("userId")
        @NotBlank(message = "userId must not be blank")
        String userId,

        @JsonProperty("status")
        @NotNull(message = "status must not be null")
        OrderStatus status,

        @JsonProperty("totalAmount")
        @NotNull(message = "totalAmount must not be null")
        @DecimalMin(value = "0.01", message = "totalAmount must be greater than 0")
        BigDecimal totalAmount
) {
}

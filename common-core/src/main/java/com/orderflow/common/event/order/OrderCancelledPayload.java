package com.orderflow.common.event.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.orderflow.common.enums.OrderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload published when an order is cancelled during saga compensation (e.g. out of stock).
 */
public record OrderCancelledPayload(
        @JsonProperty("orderId")
        @NotBlank(message = "orderId must not be blank")
        String orderId,

        @JsonProperty("userId")
        @NotBlank(message = "userId must not be blank")
        String userId,

        @JsonProperty("status")
        @NotNull(message = "status must not be null")
        OrderStatus status,

        @JsonProperty("reason")
        @NotBlank(message = "reason must not be blank")
        String reason
) {
}

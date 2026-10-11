package com.orderflow.common.event.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Published when order-service learns that stock was reserved for an order that is
 * already cancelled, so inventory must release it (saga compensation).
 */
public record StockReleaseRequestedPayload(

        @JsonProperty("orderId")
        @NotBlank(message = "orderId must not be blank")
        String orderId,

        @JsonProperty("reason")
        @NotBlank(message = "reason must not be blank")
        String reason
) {
}

package com.orderflow.common.event.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Payload published when stock reservation fails for an order (triggers Saga compensation).
 */
public record StockRejectedPayload(
        @JsonProperty("orderId")
        @NotBlank(message = "orderId must not be blank")
        String orderId,

        @JsonProperty("reason")
        @NotBlank(message = "reason must not be blank")
        String reason,

        @JsonProperty("failedProducts")
        @NotEmpty(message = "failedProducts must not be empty")
        @Valid
        List<FailedProductDto> failedProducts
) {
}

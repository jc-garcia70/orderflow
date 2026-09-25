package com.orderflow.common.event.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Details of a product that failed to be reserved due to insufficient inventory.
 */
public record FailedProductDto(
        @JsonProperty("productId")
        @NotBlank(message = "productId must not be blank")
        String productId,

        @JsonProperty("requestedQuantity")
        @NotNull(message = "requestedQuantity must not be null")
        @Min(value = 1, message = "requestedQuantity must be at least 1")
        Integer requestedQuantity,

        @JsonProperty("availableQuantity")
        @NotNull(message = "availableQuantity must not be null")
        @Min(value = 0, message = "availableQuantity cannot be negative")
        Integer availableQuantity
) {
}

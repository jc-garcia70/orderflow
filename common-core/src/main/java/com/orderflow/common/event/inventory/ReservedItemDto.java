package com.orderflow.common.event.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Item reserved successfully during inventory allocation.
 */
public record ReservedItemDto(
        @JsonProperty("productId")
        @NotBlank(message = "productId must not be blank")
        String productId,

        @JsonProperty("quantityReserved")
        @NotNull(message = "quantityReserved must not be null")
        @Min(value = 1, message = "quantityReserved must be at least 1")
        Integer quantityReserved
) {
}

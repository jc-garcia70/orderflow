package com.orderflow.common.event.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Item item detail within an order event.
 */
public record OrderItemDto(
        @JsonProperty("productId")
        @NotBlank(message = "productId must not be blank")
        String productId,

        @JsonProperty("quantity")
        @NotNull(message = "quantity must not be null")
        @Min(value = 1, message = "quantity must be at least 1")
        Integer quantity,

        @JsonProperty("unitPrice")
        @NotNull(message = "unitPrice must not be null")
        @DecimalMin(value = "0.01", message = "unitPrice must be greater than 0")
        BigDecimal unitPrice
) {
}

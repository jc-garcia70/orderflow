package com.orderflow.common.event.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Payload published when inventory for an order has been successfully reserved.
 */
public record StockReservedPayload(
        @JsonProperty("orderId")
        @NotBlank(message = "orderId must not be blank")
        String orderId,

        @JsonProperty("reservationId")
        @NotBlank(message = "reservationId must not be blank")
        String reservationId,

        @JsonProperty("items")
        @NotEmpty(message = "items list must not be empty")
        @Valid
        List<ReservedItemDto> items
) {
}

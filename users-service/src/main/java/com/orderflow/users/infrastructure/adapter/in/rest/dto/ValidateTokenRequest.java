package com.orderflow.users.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for validating token authenticity
 */
public record ValidateTokenRequest(
        @NotBlank(message = "Token must not be blank")
        String token
) {
}

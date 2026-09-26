package com.orderflow.users.infrastructure.adapter.in.rest.dto;

/**
 * Response payload indicating token validity and embedded claims.
 */
public record ValidateTokenResponse(
        boolean valid,
        String userId,
        String email,
        String role
) {
}

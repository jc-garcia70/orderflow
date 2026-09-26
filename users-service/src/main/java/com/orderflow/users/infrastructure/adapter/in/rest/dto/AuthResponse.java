package com.orderflow.users.infrastructure.adapter.in.rest.dto;

/**
 * Response payload returned upon successful authentication.
 */
public record AuthResponse(
        String token,
        String userId,
        String email,
        String fullName,
        String role
) {
}

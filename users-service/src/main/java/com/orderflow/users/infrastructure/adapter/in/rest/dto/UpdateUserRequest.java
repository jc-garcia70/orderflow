package com.orderflow.users.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


/**
 * Request payload for updating user profile.
 */
public record UpdateUserRequest(
        @NotBlank(message = "Full name must not be blank")
        @Size(min = 6, message = "Full name must be at least 6 characters")
        String fullName
) {
}

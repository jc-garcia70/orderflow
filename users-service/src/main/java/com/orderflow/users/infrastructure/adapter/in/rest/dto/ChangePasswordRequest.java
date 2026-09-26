package com.orderflow.users.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for changing user password.
 */
public record ChangePasswordRequest(

        @NotBlank(message = "Current password must not be blank")
        String currentPassword,

        @NotBlank(message = "New password must not be blank")
        @Size(min = 6, message = "New password must be at least 6 characters")
        String newPassword
) {
}

package com.orderflow.users.infrastructure.adapter.in.rest.dto;

import com.orderflow.users.domain.model.Role;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for updating user role.
 */
public record UpdateRoleRequest(
        @NotNull(message = "Role must not be null")
        Role role
) {

}

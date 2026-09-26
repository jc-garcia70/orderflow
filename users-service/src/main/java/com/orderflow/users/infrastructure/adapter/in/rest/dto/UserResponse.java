package com.orderflow.users.infrastructure.adapter.in.rest.dto;

import com.orderflow.users.domain.model.User;
import java.time.Instant;

/**
 * Public user profile response representation.
 */
public record UserResponse(
        String id,
        String email,
        String fullName,
        String role,
        boolean active,
        Instant createdAt
) {
    public static UserResponse fromDomain(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}

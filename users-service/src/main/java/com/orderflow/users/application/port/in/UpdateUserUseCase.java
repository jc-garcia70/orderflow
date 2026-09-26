package com.orderflow.users.application.port.in;

import com.orderflow.users.domain.model.Role;
import com.orderflow.users.domain.model.User;

/**
 * Inbound port for updating existing user details.
 */
public interface UpdateUserUseCase {

    /**
     * Command record containing updatable user fields.
     */
    record UpdateUserCommand(
            String userId,
            String fullName
    ){}

    /**
     * Updates an existing user profile.
     *
     * @param command update data
     * @return the updated user entity
     */
    User updateUser(UpdateUserCommand command);


    User updateRole(String userId, Role newRole);

    void deactivateUser(String userId);

}

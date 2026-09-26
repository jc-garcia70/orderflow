package com.orderflow.users.application.port.in;

import com.orderflow.users.domain.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Inbound port for retrieving user profile information.
 */
public interface GetUserProfileUseCase {


    /**
     * Retrieves a user by their unique ID.
     *
     * @param userId unique user identifier.
     * @return the found user domain entity.
     */
    User getUserById(String userId);

    /**
     *
     *
     * @param pageable
     * @return
     */
    Page<User> getAllUsers(Pageable pageable);
}

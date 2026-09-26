package com.orderflow.users.application.port.in;

import com.orderflow.users.domain.model.User;

/**
 * Inbound port defining the user registration use case.
 */
public interface RegisterUserUseCase {

    /**
     * Command record encapsulating registration input data.
     */
    record RegisterCommand(
            String email,
            String password,
            String fullName
    ){}


    /**
     * Registers a new user in the system.
     *
     * @param command input data for registration
     * @return the created user domain entity.
     */
    User register(RegisterCommand command);

}

package com.orderflow.users.application.port.in;

import com.orderflow.users.domain.model.User;

/**
 * Inbound port defining user authentication.
 */
public interface LoginUseCase {

    /**
     * Command record encapsulating login credentials.
     */
    record LoginCommand(
            String email,
            String password
    ){}

    /**
     * Result record containing authentication details.
     */
    record AuthResult(
            String token,
            User user
    ){}

    /**
     * Authenticates a user and generations an access token.
     *
     * @param command credentials.
     * @return authentication result containing token and user profile.
     */
    AuthResult login(LoginCommand command);

}

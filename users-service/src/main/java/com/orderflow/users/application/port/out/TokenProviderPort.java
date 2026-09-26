package com.orderflow.users.application.port.out;


import com.orderflow.users.domain.model.User;

/**
 * Output port for authentication token generation and validation.
 */
public interface TokenProviderPort {

    /**
     * Generates a signed access token for the given user.
     *
     * @param user the domain user for whom to issue the token
     * @return the signed token string
     */
    String generateToken(User user);

    /**
     * Extracts the subject (userID) from a given token.
     *
     * @param token the access token string
     * @return the user ID contained within the token
     */
    String extractUserId(String token);

    /**
     * Validates if a token is authentic and not expired
     *
     * @param token the JWT string to validate
     * @return if the token signature is valid and it has not expired
     */
    boolean validateToken(String token);


    /**
     *
     * @param token the JWT string from which the role will be extracted
     * @return the JWT string from which the role will be extracted
     */
    String extractRole(String token);

    /**
     *
     * @param token the access token string
     * @return the email address contained withing the token
     */
    String extractEmail(String token);

}

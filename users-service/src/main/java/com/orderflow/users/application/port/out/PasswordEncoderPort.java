package com.orderflow.users.application.port.out;

/**
 * Output port for password hashing and validation.
 */
public interface PasswordEncoderPort {

    /**
     * Hashes a raw plain-text password.
     *
     * @param rawPassword the plain-text password
     * @return the hashed password string
     */
    String encode(String rawPassword);


    /**
     * Verifies if a raw password matches an existing hashed password.
     *
     * @param rawPassword
     * @param encodePassword
     * @return
     */
    boolean matches(String rawPassword, String encodePassword);


}

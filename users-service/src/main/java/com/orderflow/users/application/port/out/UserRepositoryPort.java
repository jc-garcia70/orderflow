package com.orderflow.users.application.port.out;

import com.orderflow.users.domain.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Output port for user persistence operations.
 */
public interface UserRepositoryPort {


    /**
     * Persists or updates a user in the storage
     *
     * @param user the domain user to save
     * @return the saved user with assigned ID and timestamps
     */
    User save(User user);


    /**
     * Finds a user by their unique email address.
     *
     * @param email
     * @return
     */
    Optional<User> findByEmail(String email);


    /**
     * Finds a user by their unique identifier.
     *
     * @param id
     * @return
     */
    Optional<User> findById(String id);


    /**
     * Checks if a user already exists with the given email address.
     *
     * @param email
     * @return
     */
    boolean existsByEmail(String email);

    /**
     * Retrieves a paginated list of users.
     *
     * @param pageable pagination and sorting parameters
     * @return a page containing domain users.
     */
    Page<User> findAll(Pageable pageable);
}

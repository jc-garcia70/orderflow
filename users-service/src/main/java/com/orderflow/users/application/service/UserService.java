package com.orderflow.users.application.service;

import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.common.exception.UnauthorizedException;
import com.orderflow.users.application.port.in.GetUserProfileUseCase;
import com.orderflow.users.application.port.in.UpdateUserUseCase;
import com.orderflow.users.application.port.out.UserRepositoryPort;
import com.orderflow.users.domain.model.Role;
import com.orderflow.users.domain.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service managing user queries and profile updates.
 */
@Service
public class UserService implements GetUserProfileUseCase, UpdateUserUseCase {

    private final UserRepositoryPort userRepositoryPort;


    public UserService(UserRepositoryPort userRepositoryPort) {
        this.userRepositoryPort = userRepositoryPort;
    }


    @Override
    @Transactional(readOnly = true)
    public User getUserById(String userId) {

        return userRepositoryPort.findById(userId)
                .orElseThrow( ()-> new ResourceNotFoundException("User", userId));
    }

    @Override
    @Transactional
    public Page<User> getAllUsers(Pageable pageable) {
        return userRepositoryPort.findAll(pageable);
    }

    @Override
    @Transactional
    public User updateUser(UpdateUserCommand command) {

        // 1. Retrieve existing domain user
        User user = getUserById(command.userId());

        // 2. Apply business mutation in the domain model
        user.updateProfile(command.fullName());

        // 3. Persist updated domain entity
        return userRepositoryPort.save(user);
    }

    @Override
    @Transactional
    public User updateRole(String userId, Role newRole) {
        User user = getUserById(userId);
        user.changeRole(newRole);
        return userRepositoryPort.save(user);
    }

    @Override
    @Transactional
    public void deactivateUser(String userId) {
        User user = getUserById(userId);
        user.deactivate();
        userRepositoryPort.save(user);
    }


}

package com.orderflow.users.application.service;

import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.users.application.port.in.UpdateUserUseCase;
import com.orderflow.users.application.port.out.UserRepositoryPort;
import com.orderflow.users.domain.model.Role;
import com.orderflow.users.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for UserService use cases.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepositoryPort);
    }

    @Test
    @DisplayName("Should successfully retrieve user by ID")
    void getUserById_Success() {
        User user = new User("usr-101", "john@example.com", "hash", "John Doe", Role.ROLE_USER, true, Instant.now(), Instant.now());
        when(userRepositoryPort.findById("usr-101")).thenReturn(Optional.of(user));

        User result = userService.getUserById("usr-101");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("usr-101");
        assertThat(result.getFullName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user is not found")
    void getUserById_NotFound_ThrowsException() {
        when(userRepositoryPort.findById("unknown-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById("unknown-id"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("unknown-id");
    }

    @Test
    @DisplayName("Should return paginated users")
    void getAllUsers_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        List<User> userList = List.of(
                new User("usr-1", "user1@example.com", "hash", "User 1", Role.ROLE_USER, true, Instant.now(), Instant.now()),
                new User("usr-2", "user2@example.com", "hash", "User 2", Role.ROLE_ADMIN, true, Instant.now(), Instant.now())
        );
        Page<User> page = new PageImpl<>(userList, pageable, 2);
        when(userRepositoryPort.findAll(pageable)).thenReturn(page);

        Page<User> result = userService.getAllUsers(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("Should successfully update user profile")
    void updateUser_Success() {
        User existingUser = new User("usr-101", "john@example.com", "hash", "Old Name", Role.ROLE_USER, true, Instant.now(), Instant.now());
        when(userRepositoryPort.findById("usr-101")).thenReturn(Optional.of(existingUser));
        when(userRepositoryPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateUserUseCase.UpdateUserCommand command = new UpdateUserUseCase.UpdateUserCommand("usr-101", "New Name");
        User updated = userService.updateUser(command);

        assertThat(updated.getFullName()).isEqualTo("New Name");
        verify(userRepositoryPort).save(existingUser);
    }

    @Test
    @DisplayName("Should successfully change user role")
    void updateRole_Success() {
        User existingUser = new User("usr-101", "john@example.com", "hash", "John Doe", Role.ROLE_USER, true, Instant.now(), Instant.now());
        when(userRepositoryPort.findById("usr-101")).thenReturn(Optional.of(existingUser));
        when(userRepositoryPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = userService.updateRole("usr-101", Role.ROLE_ADMIN);

        assertThat(updated.getRole()).isEqualTo(Role.ROLE_ADMIN);
        verify(userRepositoryPort).save(existingUser);
    }

    @Test
    @DisplayName("Should successfully deactivate user")
    void deactivateUser_Success() {
        User existingUser = new User("usr-101", "john@example.com", "hash", "John Doe", Role.ROLE_USER, true, Instant.now(), Instant.now());
        when(userRepositoryPort.findById("usr-101")).thenReturn(Optional.of(existingUser));
        when(userRepositoryPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.deactivateUser("usr-101");

        assertThat(existingUser.isActive()).isFalse();
        verify(userRepositoryPort).save(existingUser);
    }
}

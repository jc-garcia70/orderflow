package com.orderflow.users.application.service;

import com.orderflow.common.exception.BusinessException;
import com.orderflow.common.exception.UnauthorizedException;
import com.orderflow.users.application.port.in.ChangePasswordUseCase;
import com.orderflow.users.application.port.in.LoginUseCase;
import com.orderflow.users.application.port.in.RegisterUserUseCase;
import com.orderflow.users.application.port.out.PasswordEncoderPort;
import com.orderflow.users.application.port.out.TokenProviderPort;
import com.orderflow.users.application.port.out.UserRepositoryPort;
import com.orderflow.users.domain.model.Role;
import com.orderflow.users.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for AuthService use cases without Spring Context.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private PasswordEncoderPort passwordEncoderPort;

    @Mock
    private TokenProviderPort tokenProviderPort;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepositoryPort, passwordEncoderPort, tokenProviderPort);
    }

    @Nested
    @DisplayName("Registration Tests")
    class RegisterTests {

        @Test
        @DisplayName("Should successfully register a new user with hashed password and active state")
        void register_Success() {
            // Arrange
            RegisterUserUseCase.RegisterCommand command = new RegisterUserUseCase.RegisterCommand(
                    "john@example.com",
                    "secret123",
                    "John Doe"
            );

            when(userRepositoryPort.existsByEmail("john@example.com")).thenReturn(false);
            when(passwordEncoderPort.encode("secret123")).thenReturn("hashed_secret");
            when(userRepositoryPort.save(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId("usr-101");
                return u;
            });

            // Act
            User createdUser = authService.register(command);

            // Assert
            assertThat(createdUser).isNotNull();
            assertThat(createdUser.getId()).isEqualTo("usr-101");
            assertThat(createdUser.getEmail()).isEqualTo("john@example.com");
            assertThat(createdUser.getPassword()).isEqualTo("hashed_secret");
            assertThat(createdUser.getRole()).isEqualTo(Role.ROLE_USER);
            assertThat(createdUser.isActive()).isTrue();

            verify(userRepositoryPort).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw BusinessException when email is already registered")
        void register_DuplicateEmail_ThrowsException() {
            // Arrange
            RegisterUserUseCase.RegisterCommand command = new RegisterUserUseCase.RegisterCommand(
                    "existing@example.com",
                    "secret123",
                    "John Doe"
            );
            when(userRepositoryPort.existsByEmail("existing@example.com")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> authService.register(command))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already exists");

            verify(userRepositoryPort, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Authentication / Login Tests")
    class LoginTests {

        @Test
        @DisplayName("Should successfully authenticate active user and return JWT token")
        void login_Success() {
            // Arrange
            LoginUseCase.LoginCommand command = new LoginUseCase.LoginCommand("john@example.com", "password123");
            User existingUser = new User("usr-101", "john@example.com", "hashed_pwd", "John Doe", Role.ROLE_USER, true, Instant.now(), Instant.now());

            when(userRepositoryPort.findByEmail("john@example.com")).thenReturn(Optional.of(existingUser));
            when(passwordEncoderPort.matches("password123", "hashed_pwd")).thenReturn(true);
            when(tokenProviderPort.generateToken(existingUser)).thenReturn("mocked.jwt.token");

            // Act
            LoginUseCase.AuthResult result = authService.login(command);

            // Assert
            assertThat(result.token()).isEqualTo("mocked.jwt.token");
            assertThat(result.user().getEmail()).isEqualTo("john@example.com");
        }

        @Test
        @DisplayName("Should throw UnauthorizedException when password does not match")
        void login_WrongPassword_ThrowsException() {
            // Arrange
            LoginUseCase.LoginCommand command = new LoginUseCase.LoginCommand("john@example.com", "wrong_password");
            User existingUser = new User("usr-101", "john@example.com", "hashed_pwd", "John Doe", Role.ROLE_USER, true, Instant.now(), Instant.now());

            when(userRepositoryPort.findByEmail("john@example.com")).thenReturn(Optional.of(existingUser));
            when(passwordEncoderPort.matches("wrong_password", "hashed_pwd")).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> authService.login(command))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid email or password");
        }

        @Test
        @DisplayName("Should throw UnauthorizedException when user account is deactivated")
        void login_DeactivatedAccount_ThrowsException() {
            // Arrange
            LoginUseCase.LoginCommand command = new LoginUseCase.LoginCommand("deactivated@example.com", "password123");
            User deactivatedUser = new User("usr-999", "deactivated@example.com", "hashed_pwd", "Inactive User", Role.ROLE_USER, false, Instant.now(), Instant.now());

            when(userRepositoryPort.findByEmail("deactivated@example.com")).thenReturn(Optional.of(deactivatedUser));

            // Act & Assert
            assertThatThrownBy(() -> authService.login(command))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("deactivated");
        }
    }

    @Nested
    @DisplayName("Password Management Tests")
    class ChangePasswordTests {

        @Test
        @DisplayName("Should successfully change password when current password matches")
        void changePassword_Success() {
            // Arrange
            ChangePasswordUseCase.ChangePasswordCommand command = new ChangePasswordUseCase.ChangePasswordCommand(
                    "usr-101",
                    "oldSecret",
                    "newSecret"
            );
            User user = new User("usr-101", "john@example.com", "hashed_old", "John Doe", Role.ROLE_USER, true, Instant.now(), Instant.now());

            when(userRepositoryPort.findById("usr-101")).thenReturn(Optional.of(user));
            when(passwordEncoderPort.matches("oldSecret", "hashed_old")).thenReturn(true);
            when(passwordEncoderPort.encode("newSecret")).thenReturn("hashed_new");

            // Act
            authService.changePassword(command);

            // Assert
            assertThat(user.getPassword()).isEqualTo("hashed_new");
            verify(userRepositoryPort).save(user);
        }

        @Test
        @DisplayName("Should throw BusinessException when current password does not match")
        void changePassword_IncorrectCurrentPassword_ThrowsException() {
            // Arrange
            ChangePasswordUseCase.ChangePasswordCommand command = new ChangePasswordUseCase.ChangePasswordCommand(
                    "usr-101",
                    "wrongOldSecret",
                    "newSecret"
            );
            User user = new User("usr-101", "john@example.com", "hashed_old", "John Doe", Role.ROLE_USER, true, Instant.now(), Instant.now());

            when(userRepositoryPort.findById("usr-101")).thenReturn(Optional.of(user));
            when(passwordEncoderPort.matches("wrongOldSecret", "hashed_old")).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> authService.changePassword(command))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Current password does not match");

            verify(userRepositoryPort, never()).save(any());
        }
    }
}

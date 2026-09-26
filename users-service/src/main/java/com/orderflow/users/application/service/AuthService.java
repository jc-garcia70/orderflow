package com.orderflow.users.application.service;

import com.orderflow.common.exception.BusinessException;
import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.common.exception.UnauthorizedException;
import com.orderflow.users.application.port.in.ChangePasswordUseCase;
import com.orderflow.users.application.port.in.LoginUseCase;
import com.orderflow.users.application.port.in.RegisterUserUseCase;
import com.orderflow.users.application.port.in.ValidateTokenUseCase;
import com.orderflow.users.application.port.out.PasswordEncoderPort;
import com.orderflow.users.application.port.out.TokenProviderPort;
import com.orderflow.users.application.port.out.UserRepositoryPort;
import com.orderflow.users.domain.model.Role;
import com.orderflow.users.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service coordinating user registration, authentication, password management
 * and token validation.
 */
@Service
public class AuthService implements RegisterUserUseCase, LoginUseCase, ChangePasswordUseCase, ValidateTokenUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoderPort passwordEncoderPort;
    private final TokenProviderPort tokenProviderPort;

    public AuthService(UserRepositoryPort userRepositoryPort, PasswordEncoderPort passwordEncoderPort, TokenProviderPort tokenProviderPort) {
        this.userRepositoryPort = userRepositoryPort;
        this.passwordEncoderPort = passwordEncoderPort;
        this.tokenProviderPort = tokenProviderPort;
    }


    @Override
    @Transactional(readOnly = true)
    public AuthResult login(LoginCommand command) {
        // 1. Find user by email, fail with generic unauthorized error to prevent enumeration
        User user = userRepositoryPort.findByEmail(command.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        // 2. Check if user account is active
        if (!user.isActive()) {
            throw new UnauthorizedException("User account has been deactivated");
        }

        // 3. Verify password match
        if (!passwordEncoderPort.matches(command.password(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        // 4. Issue signed JWT token via token provider port
        String token = tokenProviderPort.generateToken(user);

        return new AuthResult(token, user);
    }

    @Override
    @Transactional
    public User register(RegisterCommand command) {
        // 1. Business rule: Email must be unique
        if (userRepositoryPort.existsByEmail(command.email())) {
            throw new BusinessException("A user with email '" + command.email() + "' already exists");
        }

        // 2. Hash raw password using outbound port
        String hashedPassword = passwordEncoderPort.encode(command.password());

        // 3. Instantiate domain user with default user role
        User newUser = new User(
                command.email(),
                hashedPassword,
                command.fullName(),
                Role.ROLE_USER
        );

        // 4. Save and return persisted domain user
        return userRepositoryPort.save(newUser);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordCommand command) {

        User user = userRepositoryPort.findById(command.userId())
                .orElseThrow( ()-> new ResourceNotFoundException("User", command.userId()));

        // Validate that current password matches
        if(!passwordEncoderPort.matches(command.currentPassword(), user.getPassword())){
            throw new BusinessException("Current password does not match");
        }

        // Hash and assign new password
        String newHashedPassword = passwordEncoderPort.encode(command.newPassword());
        user.changePassword(newHashedPassword);

        userRepositoryPort.save(user);
    }

    @Override
    public TokenValidationResult validateToken(String token) {
        if (token != null && tokenProviderPort.validateToken(token)) {
            String userId = tokenProviderPort.extractUserId(token);
            String email = tokenProviderPort.extractEmail(token);
            String role = tokenProviderPort.extractRole(token);
            return new TokenValidationResult(true, userId, email, role);
        }
        return new TokenValidationResult(false, null, null, null);
    }

}

package com.orderflow.users.infrastructure.adapter.in.rest;

import com.orderflow.common.dto.ApiResponse;
import com.orderflow.users.application.port.in.ChangePasswordUseCase;
import com.orderflow.users.application.port.in.LoginUseCase;
import com.orderflow.users.application.port.in.ValidateTokenUseCase;
import com.orderflow.users.infrastructure.adapter.in.rest.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Primary adapter exposing authentication and token verification REST endpoints.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;

    public AuthController(LoginUseCase loginUseCase, ChangePasswordUseCase changePasswordUseCase, ValidateTokenUseCase validateTokenUseCase) {
        this.loginUseCase = loginUseCase;
        this.changePasswordUseCase = changePasswordUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login (@Valid @RequestBody
                                                            LoginRequest request){
        LoginUseCase.LoginCommand command = new LoginUseCase.LoginCommand(
                request.email(),
                request.password()
        );

        LoginUseCase.AuthResult result = loginUseCase.login(command);

        AuthResponse authResponse = new AuthResponse(
                result.token(),
                result.user().getId(),
                result.user().getEmail(),
                result.user().getFullName(),
                result.user().getRole().name()

        );

        return ResponseEntity.ok(ApiResponse.success("Authentication successful", authResponse));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        // Retrieve authenticated userId set in SecurityContext by JwtAuthenticationFilter
        String userId = authentication.getName();
        ChangePasswordUseCase.ChangePasswordCommand command = new ChangePasswordUseCase.ChangePasswordCommand(
                userId,
                request.currentPassword(),
                request.newPassword()
        );
        changePasswordUseCase.changePassword(command);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<ValidateTokenResponse>> validateToken(@Valid @RequestBody ValidateTokenRequest request) {
        ValidateTokenUseCase.TokenValidationResult result = validateTokenUseCase.validateToken(request.token());
        ValidateTokenResponse response = new ValidateTokenResponse(
                result.valid(),
                result.userId(),
                result.email(),
                result.role()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

}

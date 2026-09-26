package com.orderflow.users.infrastructure.adapter.in.rest;

import com.orderflow.common.dto.ApiResponse;
import com.orderflow.users.application.port.in.GetUserProfileUseCase;
import com.orderflow.users.application.port.in.RegisterUserUseCase;
import com.orderflow.users.application.port.in.UpdateUserUseCase;
import com.orderflow.users.domain.model.User;
import com.orderflow.users.infrastructure.adapter.in.rest.dto.RegisterRequest;
import com.orderflow.users.infrastructure.adapter.in.rest.dto.UpdateRoleRequest;
import com.orderflow.users.infrastructure.adapter.in.rest.dto.UpdateUserRequest;
import com.orderflow.users.infrastructure.adapter.in.rest.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Primary adapter exposing comprehensive user management REST endpoints.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final RegisterUserUseCase registerUserUseCase;
    private final GetUserProfileUseCase getUserProfileUseCase;
    private final UpdateUserUseCase updateUserUseCase;


    public UserController(RegisterUserUseCase registerUserUseCase, GetUserProfileUseCase getUserProfileUseCase, UpdateUserUseCase updateUserUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.getUserProfileUseCase = getUserProfileUseCase;
        this.updateUserUseCase = updateUserUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterUserUseCase.RegisterCommand command = new RegisterUserUseCase.RegisterCommand(
                request.email(),
                request.password(),
                request.fullName()
        );
        User registeredUser = registerUserUseCase.register(command);
        UserResponse response = UserResponse.fromDomain(registeredUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(Authentication authentication) {
        String currentUserId = authentication.getName();
        User user = getUserProfileUseCase.getUserById(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(UserResponse.fromDomain(user)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(
            @PathVariable String id,
            Authentication authentication) {
        checkOwnershipOrAdmin(authentication, id, "view");
        User user = getUserProfileUseCase.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(UserResponse.fromDomain(user)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        Page<UserResponse> usersPage = getUserProfileUseCase.getAllUsers(pageable)
                .map(UserResponse::fromDomain);
        return ResponseEntity.ok(ApiResponse.success(usersPage));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @PathVariable String id,
            Authentication authentication,
            @Valid @RequestBody UpdateUserRequest request) {
        checkOwnershipOrAdmin(authentication, id, "update");
        UpdateUserUseCase.UpdateUserCommand command = new UpdateUserUseCase.UpdateUserCommand(
                id,
                request.fullName()
        );
        User updatedUser = updateUserUseCase.updateUser(command);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", UserResponse.fromDomain(updatedUser)));
    }

    private void checkOwnershipOrAdmin(Authentication authentication, String targetUserId, String action) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !authentication.getName().equals(targetUserId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not have permission to " + action + " another user's profile"
            );
        }
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<UserResponse>> updateRole(
            @PathVariable String id,
            @Valid @RequestBody UpdateRoleRequest request) {
        User updatedUser = updateUserUseCase.updateRole(id, request.role());
        return ResponseEntity.ok(ApiResponse.success("Role updated successfully", UserResponse.fromDomain(updatedUser)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(@PathVariable String id) {
        updateUserUseCase.deactivateUser(id);
        return ResponseEntity.ok(ApiResponse.success("User account deactivated successfully", null));
    }

}

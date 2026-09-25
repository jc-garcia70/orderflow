package com.orderflow.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    @DisplayName("Should create success ApiResponse with correct defaults")
    void testSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("test-data");
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Operation successful");
        assertThat(response.data()).isEqualTo("test-data");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should create error ApiResponse correctly")
    void testErrorResponse() {
        ApiResponse<Void> response = ApiResponse.error("Something went wrong");
        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("Something went wrong");
        assertThat(response.data()).isNull();
    }

    @Test
    @DisplayName("Should create ErrorResponse with validation errors")
    void testErrorResponseWithValidation() {
        List<ValidationError> errors = List.of(
                new ValidationError("email", "must be a valid email address"),
                new ValidationError("password", "must be at least 8 characters")
        );
        ErrorResponse errorResponse = ErrorResponse.of(
                400,
                "Bad Request",
                "Validation failed for one or more fields",
                "/api/v1/users",
                errors
        );

        assertThat(errorResponse.status()).isEqualTo(400);
        assertThat(errorResponse.error()).isEqualTo("Bad Request");
        assertThat(errorResponse.validationErrors()).hasSize(2);
        assertThat(errorResponse.validationErrors().getFirst().field()).isEqualTo("email");
    }
}

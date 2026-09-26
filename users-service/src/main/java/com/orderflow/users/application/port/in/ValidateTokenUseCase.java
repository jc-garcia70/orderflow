package com.orderflow.users.application.port.in;

/**
 * Inbound port for verifying JWT authenticity (useful for API Gateway)
 */
public interface ValidateTokenUseCase {

    record TokenValidationResult(
            boolean valid,
            String userId,
            String email,
            String role
    ){}

    TokenValidationResult validateToken(String token);

}

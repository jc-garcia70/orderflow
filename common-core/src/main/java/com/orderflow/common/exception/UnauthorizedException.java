package com.orderflow.common.exception;

/**
 * Thrown when an unauthenticated or invalid credential access attempt is made.
 */
public class UnauthorizedException extends OrderFlowException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}

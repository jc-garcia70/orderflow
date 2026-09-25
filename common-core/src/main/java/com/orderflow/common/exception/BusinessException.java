package com.orderflow.common.exception;

/**
 * Thrown when a business rule or validation constraint is violated.
 */
public class BusinessException extends OrderFlowException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}

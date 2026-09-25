package com.orderflow.common.exception;

/**
 * Thrown when a requested resource (user, order, product, etc.) cannot be found.
 */
public class ResourceNotFoundException extends OrderFlowException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(String.format("%s with identifier '%s' was not found", resourceName, identifier));
    }
}

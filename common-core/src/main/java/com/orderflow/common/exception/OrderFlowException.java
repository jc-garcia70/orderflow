package com.orderflow.common.exception;

/**
 * Base abstract runtime exception for the OrderFlow microservices ecosystem.
 */
public abstract class OrderFlowException extends RuntimeException {

    protected OrderFlowException(String message) {
        super(message);
    }

    protected OrderFlowException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.orderflow.common.exception;

/**
 * Thrown when an optimistic locking (@Version) conflict or race condition occurs
 * during concurrent database updates.
 */
public class ConcurrencyConflictException extends OrderFlowException {

    public ConcurrencyConflictException(String message) {
        super(message);
    }

    public ConcurrencyConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}

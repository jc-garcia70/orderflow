package com.orderflow.orders.application.exception;

public class InventoryServiceUnavailableException extends RuntimeException {

    public InventoryServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

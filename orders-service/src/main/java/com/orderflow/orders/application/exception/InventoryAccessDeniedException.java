package com.orderflow.orders.application.exception;

/**
 * Inventory rejected our service credentials (401/403). It is a deployment/configuration
 * problem, so it is NOT retried, but to the client it is still a 503.
 */
public class InventoryAccessDeniedException extends InventoryServiceUnavailableException  {

    public InventoryAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }

}

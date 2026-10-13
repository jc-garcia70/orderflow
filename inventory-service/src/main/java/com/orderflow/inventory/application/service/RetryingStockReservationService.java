package com.orderflow.inventory.application.service;

import com.orderflow.inventory.application.port.in.StockReservationUseCase;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Entry point of the stock reservation use case for inbound adapters.
 * Deliberately NOT transactional: each delegate call opens its own transaction,
 * so a retry after an optimistic lock conflict starts from a clean state.
 */
@Service
@Primary
public class RetryingStockReservationService implements StockReservationUseCase {

    private final StockReservationService delegate;
    private final OptimisticLockRetry retry;

    public RetryingStockReservationService(StockReservationService delegate, OptimisticLockRetry retry) {
        this.delegate = delegate;
        this.retry = retry;
    }

    @Override
    public void reserveStock(ReserveStockCommand command) {
        retry.run("ReserveStock(order= " + command.orderId() + ")", ()-> delegate.reserveStock(command));
    }

    @Override
    public void releaseStock(ReleaseStockCommand command) {
        retry.run("releaseStock(order=" + command.orderId() + ")", () -> delegate.releaseStock(command));
    }

    @Override
    public void confirmStock(ConfirmStockCommand command) {
        retry.run("confirmStock(order=" + command.orderId() + ")", () -> delegate.confirmStock(command));
    }

}

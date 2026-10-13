package com.orderflow.inventory.application.service;

import com.orderflow.inventory.application.port.in.StockReservationUseCase.ConfirmStockCommand;
import com.orderflow.inventory.application.port.in.StockReservationUseCase.OrderItemRequest;
import com.orderflow.inventory.application.port.in.StockReservationUseCase.ReleaseStockCommand;
import com.orderflow.inventory.application.port.in.StockReservationUseCase.ReserveStockCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RetryingStockReservationServiceTest {

    @Mock
    private StockReservationService delegate;

    private RetryingStockReservationService service() {
        return new RetryingStockReservationService(
                delegate, new OptimisticLockRetry(3, 1, 1, millis -> { }));
    }

    @Test
    @DisplayName("reserveStock is re-invoked on the delegate after a conflict")
    void reserveStock_retriesOnConflict() {
        ReserveStockCommand command = new ReserveStockCommand(
                "ord-1", List.of(new OrderItemRequest("p1", 2)));
        doThrow(new OptimisticLockingFailureException("conflict"))
                .doNothing()
                .when(delegate).reserveStock(command);

        service().reserveStock(command);

        verify(delegate, times(2)).reserveStock(command);
    }

    @Test
    @DisplayName("confirmStock is re-invoked on the delegate after a conflict")
    void confirmStock_retriesOnConflict() {
        ConfirmStockCommand command = new ConfirmStockCommand("ord-1");
        doThrow(new OptimisticLockingFailureException("conflict"))
                .doNothing()
                .when(delegate).confirmStock(command);

        service().confirmStock(command);

        verify(delegate, times(2)).confirmStock(command);
    }

    @Test
    @DisplayName("releaseStock delegates once when there is no conflict")
    void releaseStock_delegates() {
        ReleaseStockCommand command = new ReleaseStockCommand("ord-1");
        doNothing().when(delegate).releaseStock(command);

        service().releaseStock(command);

        verify(delegate, times(1)).releaseStock(command);
    }
}
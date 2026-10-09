package com.orderflow.inventory.application.service;

import com.orderflow.common.event.inventory.StockRejectedPayload;
import com.orderflow.common.event.inventory.StockReservedPayload;
import com.orderflow.inventory.application.port.in.StockReservationUseCase.ConfirmStockCommand;
import com.orderflow.inventory.application.port.in.StockReservationUseCase.OrderItemRequest;
import com.orderflow.inventory.application.port.in.StockReservationUseCase.ReleaseStockCommand;
import com.orderflow.inventory.application.port.in.StockReservationUseCase.ReserveStockCommand;
import com.orderflow.inventory.application.port.out.InventoryEventPublisherPort;
import com.orderflow.inventory.application.port.out.ProcessedOrderReservationPort;
import com.orderflow.inventory.application.port.out.ProductRepositoryPort;
import com.orderflow.inventory.domain.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockReservationServiceTest {

    @Mock
    private ProductRepositoryPort productRepositoryPort;

    @Mock
    private InventoryEventPublisherPort eventPublisherPort;

    @Mock
    private ProcessedOrderReservationPort processedOrderReservationPort;

    @InjectMocks
    private StockReservationService stockReservationService;

    private Product createProduct(String id, String sku, int available) {
        return new Product(
                id,
                sku,
                "Product " + sku,
                "Description",
                new BigDecimal("50.00"),
                available,
                0,
                true,
                1L,
                null,
                null
        );
    }

    @Nested
    @DisplayName("Stock Reservation Flow")
    class ReservationTests {

        @Test
        @DisplayName("Should ignore duplicate order reservation requests")
        void shouldIgnoreDuplicateReservationRequest() {
            when(processedOrderReservationPort.existsByOrderId("ord-duplicate"))
                    .thenReturn(true);

            stockReservationService.reserveStock(new ReserveStockCommand(
                    "ord-duplicate",
                    List.of(new OrderItemRequest("p1", 2))
            ));

            verify(processedOrderReservationPort, never()).markProcessed(any());
            verifyNoInteractions(productRepositoryPort, eventPublisherPort);
        }

        @Test
        @DisplayName("Should successfully reserve stock and publish StockReservedPayload when all items are available")
        void shouldReserveStockSuccessfully() {
            Product prod1 = createProduct("p1", "SKU-1", 10);
            Product prod2 = createProduct("p2", "SKU-2", 5);
            when(productRepositoryPort.findById("p1")).thenReturn(Optional.of(prod1));
            when(productRepositoryPort.findById("p2")).thenReturn(Optional.of(prod2));
            ReserveStockCommand command = new ReserveStockCommand(
                    "ord-100",
                    List.of(
                            new OrderItemRequest("p1", 2),
                            new OrderItemRequest("p2", 3)
                    )
            );
            stockReservationService.reserveStock(command);
            assertThat(prod1.getAvailableQuantity()).isEqualTo(8);
            assertThat(prod1.getReservedQuantity()).isEqualTo(2);
            assertThat(prod2.getAvailableQuantity()).isEqualTo(2);
            assertThat(prod2.getReservedQuantity()).isEqualTo(3);
            verify(productRepositoryPort, times(2)).save(any(Product.class));
            ArgumentCaptor<StockReservedPayload> captor = ArgumentCaptor.forClass(StockReservedPayload.class);
            verify(eventPublisherPort).publishStockReserved(captor.capture());
            StockReservedPayload published = captor.getValue();
            assertThat(published.orderId()).isEqualTo("ord-100");
            assertThat(published.reservationId()).isNotBlank();
            assertThat(published.items()).hasSize(2);
            verify(eventPublisherPort, never()).publishStockRejected(any());
        }

        @Test
        @DisplayName("Should reject reservation and publish StockRejectedPayload when any item has insufficient stock")
        void shouldRejectWhenInsufficientStock() {
            Product prod1 = createProduct("p1", "SKU-1", 10);
            Product prod2 = createProduct("p2", "SKU-2", 1); // Only 1 available, 3 requested!
            when(productRepositoryPort.findById("p1")).thenReturn(Optional.of(prod1));
            when(productRepositoryPort.findById("p2")).thenReturn(Optional.of(prod2));
            ReserveStockCommand command = new ReserveStockCommand(
                    "ord-101",
                    List.of(
                            new OrderItemRequest("p1", 2),
                            new OrderItemRequest("p2", 3)
                    )
            );
            stockReservationService.reserveStock(command);
            // Invariants: DB mutations must be aborted
            verify(productRepositoryPort, never()).save(any(Product.class));
            verify(eventPublisherPort, never()).publishStockReserved(any());
            ArgumentCaptor<StockRejectedPayload> captor = ArgumentCaptor.forClass(StockRejectedPayload.class);
            verify(eventPublisherPort).publishStockRejected(captor.capture());
            StockRejectedPayload rejected = captor.getValue();
            assertThat(rejected.orderId()).isEqualTo("ord-101");
            assertThat(rejected.reason()).isEqualTo("INSUFFICIENT_STOCK");
            assertThat(rejected.failedProducts()).hasSize(1);
            assertThat(rejected.failedProducts().getFirst().productId()).isEqualTo("p2");
            assertThat(rejected.failedProducts().getFirst().availableQuantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should reject reservation when product does not exist")
        void shouldRejectWhenProductNotFound() {
            when(productRepositoryPort.findById("p-missing")).thenReturn(Optional.empty());
            ReserveStockCommand command = new ReserveStockCommand(
                    "ord-102",
                    List.of(new OrderItemRequest("p-missing", 1))
            );
            stockReservationService.reserveStock(command);
            verify(productRepositoryPort, never()).save(any(Product.class));
            ArgumentCaptor<StockRejectedPayload> captor = ArgumentCaptor.forClass(StockRejectedPayload.class);
            verify(eventPublisherPort).publishStockRejected(captor.capture());
            assertThat(captor.getValue().reason()).isEqualTo("PRODUCT_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("Saga Compensation and Confirmation")
    class CompensationAndConfirmationTests {
        @Test
        @DisplayName("Should release reserved stock upon compensation")
        void shouldReleaseStock() {
            Product product = new Product("p1", "SKU-1", "Prod", "Desc", BigDecimal.TEN, 5, 5, true, 1L, null, null);
            when(productRepositoryPort.findById("p1")).thenReturn(Optional.of(product));
            ReleaseStockCommand command = new ReleaseStockCommand(
                    "ord-100",
                    List.of(new OrderItemRequest("p1", 3))
            );
            stockReservationService.releaseStock(command);
            assertThat(product.getAvailableQuantity()).isEqualTo(8);
            assertThat(product.getReservedQuantity()).isEqualTo(2);
            verify(productRepositoryPort).save(product);
        }

        @Test
        @DisplayName("Should confirm reserved stock permanently")
        void shouldConfirmStock() {
            Product product = new Product("p1", "SKU-1", "Prod", "Desc", BigDecimal.TEN, 5, 5, true, 1L, null, null);
            when(productRepositoryPort.findById("p1")).thenReturn(Optional.of(product));
            ConfirmStockCommand command = new ConfirmStockCommand(
                    "ord-100",
                    List.of(new OrderItemRequest("p1", 4))
            );
            stockReservationService.confirmStock(command);
            assertThat(product.getAvailableQuantity()).isEqualTo(5);
            assertThat(product.getReservedQuantity()).isEqualTo(1);
            verify(productRepositoryPort).save(product);
        }

    }


}

package com.orderflow.inventory.domain.model;

import com.orderflow.common.exception.BusinessException;
import com.orderflow.common.exception.InsufficientStockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private Product createSampleProduct(int available, int reserved, boolean active) {
        return new Product(
                "prod-123",
                "PROD-TEST-001",
                "Mechanical Keyboard",
                "RGB mechanical keyboard",
                new BigDecimal("99.99"),
                available,
                reserved,
                active,
                1L,
                null,
                null
        );
    }

    @Nested
    @DisplayName("Stock Reservation Rules")
    class StockReservationTests {

        @Test
        @DisplayName("Should successfully reserve stock when sufficient available quantity exists")
        void shouldSuccessfullyReserveStock() {
            Product product = createSampleProduct(10, 0, true);

            product.reserveStock(4);

            assertThat(product.getAvailableQuantity()).isEqualTo(6);
            assertThat(product.getReservedQuantity()).isEqualTo(4);
            assertThat(product.getTotalQuantity()).isEqualTo(10);
        }

        @Test
        @DisplayName("Should throw InsufficientStockException when requested quantity exceeds available stock")
        void shouldThrowInsufficientStockExceptionWhenNotEnoughStock() {
            Product product = createSampleProduct(3, 0, true);

            assertThatThrownBy(() -> product.reserveStock(5))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("Insufficient stock")
                    .satisfies(ex -> {
                        InsufficientStockException ise = (InsufficientStockException) ex;
                        assertThat(ise.getProductId()).isEqualTo("prod-123");
                        assertThat(ise.getRequestedQuantity()).isEqualTo(5);
                        assertThat(ise.getAvailableQuantity()).isEqualTo(3);
                    });
        }

        @Test
        @DisplayName("Should throw BusinessException when attempting to reserve stock of an inactive product")
        void shouldThrowBusinessExceptionWhenProductIsInactive() {
            Product product = createSampleProduct(10, 0, false);

            assertThatThrownBy(() -> product.reserveStock(2))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("inactive and cannot be reserved");
        }

        @Test
        @DisplayName("Should throw BusinessException when requested reservation quantity is zero or negative")
        void shouldThrowBusinessExceptionWhenReservationQuantityZeroOrNegative() {
            Product product = createSampleProduct(10, 0, true);

            assertThatThrownBy(() -> product.reserveStock(0))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("strictly greater than zero");

            assertThatThrownBy(() -> product.reserveStock(-3))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("strictly greater than zero");
        }
    }

    @Nested
    @DisplayName("Stock Release (Compensation) Rules")
    class StockReleaseTests {

        @Test
        @DisplayName("Should successfully release reserved stock back to available pool")
        void shouldSuccessfullyReleaseStock() {
            Product product = createSampleProduct(6, 4, true);

            product.releaseStock(3);

            assertThat(product.getAvailableQuantity()).isEqualTo(9);
            assertThat(product.getReservedQuantity()).isEqualTo(1);
            assertThat(product.getTotalQuantity()).isEqualTo(10);
        }

        @Test
        @DisplayName("Should throw BusinessException when releasing more than currently reserved")
        void shouldThrowBusinessExceptionWhenReleasingMoreThanReserved() {
            Product product = createSampleProduct(6, 2, true);

            assertThatThrownBy(() -> product.releaseStock(3))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot release 3 units");
        }
    }

    @Nested
    @DisplayName("Stock Confirmation (Commit) Rules")
    class StockConfirmationTests {

        @Test
        @DisplayName("Should successfully commit reserved stock upon order confirmation")
        void shouldSuccessfullyConfirmReservation() {
            Product product = createSampleProduct(5, 5, true);

            product.confirmReservation(3);

            assertThat(product.getAvailableQuantity()).isEqualTo(5);
            assertThat(product.getReservedQuantity()).isEqualTo(2);
            assertThat(product.getTotalQuantity()).isEqualTo(7);
        }

        @Test
        @DisplayName("Should throw BusinessException when confirming more than currently reserved")
        void shouldThrowBusinessExceptionWhenConfirmingMoreThanReserved() {
            Product product = createSampleProduct(5, 2, true);

            assertThatThrownBy(() -> product.confirmReservation(3))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot confirm 3 units");
        }
    }

    @Nested
    @DisplayName("Restocking and Lifecycle Rules")
    class RestockingAndLifecycleTests {

        @Test
        @DisplayName("Should successfully restock available inventory")
        void shouldSuccessfullyRestock() {
            Product product = createSampleProduct(5, 2, true);

            product.restock(15);

            assertThat(product.getAvailableQuantity()).isEqualTo(20);
            assertThat(product.getReservedQuantity()).isEqualTo(2);
            assertThat(product.getTotalQuantity()).isEqualTo(22);
        }

        @Test
        @DisplayName("Should toggle product active status")
        void shouldToggleActiveStatus() {
            Product product = createSampleProduct(10, 0, true);

            product.deactivate();
            assertThat(product.isActive()).isFalse();

            product.activate();
            assertThat(product.isActive()).isTrue();
        }

        @Test
        @DisplayName("Should update product informational details")
        void shouldUpdateDetails() {
            Product product = createSampleProduct(10, 0, true);

            product.updateDetails("Updated Keyboard", "New Description", new BigDecimal("129.99"));

            assertThat(product.getName()).isEqualTo("Updated Keyboard");
            assertThat(product.getDescription()).isEqualTo("New Description");
            assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("129.99"));
        }
    }
}

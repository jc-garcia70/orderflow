package com.orderflow.orders.domain.model;

import com.orderflow.common.enums.OrderStatus;
import com.orderflow.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private OrderItem createSampleItem(String productId, int quantity, String price) {
        return new OrderItem(productId, quantity, new BigDecimal(price));
    }

    @Nested
    @DisplayName("Order Creation Rules")
    class CreationTests {

        @Test
        @DisplayName("Should create order in PENDIENTE status and calculate total amount correctly")
        void shouldCreateOrderSuccessfully() {
            List<OrderItem> items = List.of(
                    createSampleItem("prod-1", 2, "50.00"), // 100.00
                    createSampleItem("prod-2", 1, "25.50")  // 25.50
            );
            Order order = new Order("usr-123", items);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getUserId()).isEqualTo("usr-123");
            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("125.50"));
            assertThat(order.getCancellationReason()).isNull();
            assertThat(order.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should throw BusinessException when order has no items")
        void shouldThrowWhenItemsListIsEmpty() {
            assertThatThrownBy(() -> new Order("usr-123", Collections.emptyList()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("at least one item");
        }

        @Test
        @DisplayName("Should throw BusinessException when userId is blank")
        void shouldThrowWhenUserIdIsBlank() {
            List<OrderItem> items = List.of(createSampleItem("prod-1", 1, "10.00"));
            assertThatThrownBy(() -> new Order("   ", items))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("User ID cannot be null or blank");
        }
    }

    @Nested
    @DisplayName("Order Confirmation Rules")
    class ConfirmationTests {

        @Test
        @DisplayName("Should successfully confirm an order in PENDIENTE status")
        void shouldConfirmPendingOrder() {
            Order order = new Order("usr-123", List.of(createSampleItem("prod-1", 1, "10.00")));
            order.confirm();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("Should throw BusinessException when attempting to confirm an already confirmed or cancelled order")
        void shouldThrowWhenConfirmingNonPendingOrder() {
            Order order = new Order("usr-123", List.of(createSampleItem("prod-1", 1, "10.00")));
            order.confirm();
            assertThatThrownBy(order::confirm)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Only PENDING orders can be confirmed");
        }
    }

    @Nested
    @DisplayName("Order Cancellation Rules")
    class CancellationTests {

        @Test
        @DisplayName("Should successfully cancel a PENDIENTE order with reason")
        void shouldCancelPendingOrder() {
            Order order = new Order("usr-123", List.of(createSampleItem("prod-1", 1, "10.00")));
            order.cancel("INSUFFICIENT_STOCK");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getCancellationReason()).isEqualTo("INSUFFICIENT_STOCK");
        }

        @Test
        @DisplayName("Should throw BusinessException when attempting to cancel an already confirmed order")
        void shouldThrowWhenCancellingConfirmedOrder() {
            Order order = new Order("usr-123", List.of(createSampleItem("prod-1", 1, "10.00")));
            order.confirm();
            assertThatThrownBy(() -> order.cancel("ANY_REASON"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Only PENDING orders can be cancelled");
        }

        @Test
        @DisplayName("Should throw BusinessException when cancellation reason is blank")
        void shouldThrowWhenReasonIsBlank() {
            Order order = new Order("usr-123", List.of(createSampleItem("prod-1", 1, "10.00")));
            assertThatThrownBy(() -> order.cancel("  "))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cancellation reason cannot be null or blank");
        }
    }
}
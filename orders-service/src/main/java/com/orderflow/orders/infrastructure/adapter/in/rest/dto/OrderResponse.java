package com.orderflow.orders.infrastructure.adapter.in.rest.dto;

import com.orderflow.orders.domain.model.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(

        String id,
        String userId,
        String status,
        List<OrderItemResponse> items,
        BigDecimal totalAmount,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt
) {

    public static OrderResponse fromDomain(Order order){
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getId(),
                        item.getProductId(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getSubtotal()
                ))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus().name(),
                itemResponses,
                order.getTotalAmount(),
                order.getCancellationReason(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }


    public record OrderItemResponse(
            String id,
            String productId,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
    }

}

package com.orderflow.orders.infrastructure.adapter.in.rest;

import com.orderflow.common.dto.ApiResponse;
import com.orderflow.orders.application.port.in.CreateOrderUseCase;
import com.orderflow.orders.application.port.in.GetOrderUseCase;
import com.orderflow.orders.domain.model.Order;
import com.orderflow.orders.infrastructure.adapter.in.rest.dto.CreateOrderRequest;
import com.orderflow.orders.infrastructure.adapter.in.rest.dto.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;

    public OrderController(
            CreateOrderUseCase createOrderUseCase,
            GetOrderUseCase getOrderUseCase
    ) {
        this.createOrderUseCase = createOrderUseCase;
        this.getOrderUseCase = getOrderUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            Authentication authentication,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        List<CreateOrderUseCase.OrderItemCommand> items = request.items().stream()
                .map(item -> new CreateOrderUseCase.OrderItemCommand(
                        item.productId(),
                        item.quantity()
                ))
                .toList();

        Order order = createOrderUseCase.createOrder(
                new CreateOrderUseCase.CreateOrderCommand(
                        authentication.getName(),
                        items
                )
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Order created successfully",
                        OrderResponse.fromDomain(order)
                ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @PathVariable String id,
            Authentication authentication
    ) {
        Order order = getOrderUseCase.getOrderById(id);

        if (!canAccessUser(order.getUserId(), authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.<OrderResponse>error(
                            "You do not have permission to access this order"
                    ));
        }

        return ResponseEntity.ok(
                ApiResponse.success(OrderResponse.fromDomain(order))
        );
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getOrdersByUserId(
            @PathVariable String userId,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication
    ) {
        if (!canAccessUser(userId, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.<Page<OrderResponse>>error(
                            "You do not have permission to access these orders"
                    ));
        }

        Page<OrderResponse> orders = getOrderUseCase
                .getOrdersByUserId(userId, pageable)
                .map(OrderResponse::fromDomain);

        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getAllOrders(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication
    ) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.<Page<OrderResponse>>error(
                            "Only administrators can access all orders"
                    ));
        }

        Page<OrderResponse> orders = getOrderUseCase
                .getAllOrders(pageable)
                .map(OrderResponse::fromDomain);

        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    private boolean canAccessUser(String userId, Authentication authentication) {
        return isAdmin(authentication) || authentication.getName().equals(userId);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}

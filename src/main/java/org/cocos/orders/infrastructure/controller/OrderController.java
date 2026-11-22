package org.cocos.orders.infrastructure.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.cocos.orders.application.OrderService;
import org.cocos.orders.infrastructure.dto.request.CreateOrderRequest;
import org.cocos.orders.infrastructure.dto.response.OrderResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/{userId}/orders")
@RequiredArgsConstructor
@Tag(name = "Orders")
public class OrderController {

    private final OrderService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a buy or sell order",
            description = "Send exactly one of `quantity` or `amount`. Insufficient resources persist a REJECTED order.")
    public OrderResponse create(
            @PathVariable Integer userId,
            @Valid @RequestBody CreateOrderRequest request) {
        return OrderResponse.from(service.create(userId, request));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel a pending order")
    public OrderResponse cancel(
            @PathVariable Integer userId,
            @PathVariable Integer orderId) {
        return OrderResponse.from(service.cancel(userId, orderId));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get one order of a user")
    public OrderResponse get(
            @PathVariable Integer userId,
            @PathVariable Integer orderId) {
        return OrderResponse.from(service.get(userId, orderId));
    }
}

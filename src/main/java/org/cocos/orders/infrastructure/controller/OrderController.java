package org.cocos.orders.infrastructure.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.cocos.orders.application.OrderService;
import org.cocos.orders.infrastructure.dto.request.CreateOrderRequest;
import org.cocos.orders.infrastructure.dto.response.OrderResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/users/{userId}/orders")
@RequiredArgsConstructor
@Tag(name = "Orders")
public class OrderController {

    private final OrderService service;

    @PostMapping
    @Operation(summary = "Submit a buy or sell order",
            description = "Send exactly one of `quantity` or `amount`. "
                    + "Insufficient resources persist a REJECTED order.")
    @ApiResponse(responseCode = "201", description = "Order created.",
            headers = @Header(name = "Location", description = "URL of the created order."))
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND, INSTRUMENT_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "MARKET_PRICE_UNAVAILABLE, INCONSISTENT_POSITION, ACCOUNT_BUSY")
    @ApiResponse(responseCode = "415", description = "UNSUPPORTED_MEDIA_TYPE")
    @ApiResponse(responseCode = "422", description = "INSTRUMENT_NOT_TRADABLE, SIZE_OUT_OF_RANGE, AMOUNT_TOO_SMALL")
    public ResponseEntity<OrderResponse> create(
            @PathVariable Integer userId,
            @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse order = OrderResponse.from(service.create(userId, request));
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{orderId}").build(order.id()))
                .body(order);
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel a pending order")
    @ApiResponse(responseCode = "200", description = "Order cancelled.")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND, ORDER_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "ORDER_NOT_CANCELLABLE, ACCOUNT_BUSY")
    public OrderResponse cancel(
            @PathVariable Integer userId,
            @PathVariable Integer orderId) {
        return OrderResponse.from(service.cancel(userId, orderId));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get one order of a user")
    @ApiResponse(responseCode = "200", description = "Order found.")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND, ORDER_NOT_FOUND")
    public OrderResponse get(
            @PathVariable Integer userId,
            @PathVariable Integer orderId) {
        return OrderResponse.from(service.get(userId, orderId));
    }
}

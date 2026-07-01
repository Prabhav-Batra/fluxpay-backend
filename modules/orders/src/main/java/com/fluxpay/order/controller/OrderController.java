package com.fluxpay.order.controller;

import com.fluxpay.order.dto.OrderCreateRequest;
import com.fluxpay.order.dto.OrderDto;
import com.fluxpay.order.service.OrderService;
import com.fluxpay.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderDto>> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        OrderDto order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(order, "Order created successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderDto>> getOrder(@PathVariable UUID id) {
        OrderDto order = orderService.getOrder(id);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<OrderDto>>> getOrdersByMerchant(@PathVariable UUID merchantId) {
        List<OrderDto> orders = orderService.getOrdersByMerchant(merchantId);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }
}

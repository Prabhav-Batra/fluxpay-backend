package com.fluxpay.payment.controller;

import com.fluxpay.payment.dto.PaymentIntentDto;
import com.fluxpay.payment.dto.ProcessPaymentRequest;
import com.fluxpay.payment.service.PaymentService;
import com.fluxpay.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/process")
    public ResponseEntity<ApiResponse<PaymentIntentDto>> processPayment(@Valid @RequestBody ProcessPaymentRequest request) {
        PaymentIntentDto intent = paymentService.processPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(intent, "Payment processing initiated"));
    }

    @GetMapping("/intent/{id}")
    public ResponseEntity<ApiResponse<PaymentIntentDto>> getPaymentIntent(@PathVariable UUID id) {
        PaymentIntentDto intent = paymentService.getPaymentIntent(id);
        return ResponseEntity.ok(ApiResponse.success(intent));
    }
}

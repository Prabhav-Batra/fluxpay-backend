package com.fluxpay.api.checkout.controller;

import com.fluxpay.api.checkout.service.CheckoutSessionService;
import com.fluxpay.api.checkout.dto.CheckoutSessionDto;
import com.fluxpay.api.checkout.dto.CheckoutSessionRequest;
import com.fluxpay.api.checkout.dto.CheckoutSessionResponse;
import com.fluxpay.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/checkout/sessions")
@RequiredArgsConstructor
public class CheckoutSessionController {

    private final CheckoutSessionService checkoutSessionService;

    @PostMapping
    public ResponseEntity<ApiResponse<CheckoutSessionResponse>> createSession(@Valid @RequestBody CheckoutSessionRequest request) {
        CheckoutSessionResponse response = checkoutSessionService.createSession(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Checkout session created"));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<CheckoutSessionDto>> getSession(@PathVariable String sessionId) {
        CheckoutSessionDto session = checkoutSessionService.getSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success(session, "Checkout session retrieved"));
    }
}

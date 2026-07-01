package com.fluxpay.subscription.controller;

import com.fluxpay.shared.dto.ApiResponse;
import com.fluxpay.subscription.dto.SubscriptionCreateRequest;
import com.fluxpay.subscription.dto.SubscriptionDto;
import com.fluxpay.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionDto>> createSubscription(@Valid @RequestBody SubscriptionCreateRequest request) {
        SubscriptionDto subscription = subscriptionService.createSubscription(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(subscription, "Subscription created successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SubscriptionDto>> getSubscription(@PathVariable UUID id) {
        SubscriptionDto subscription = subscriptionService.getSubscription(id);
        return ResponseEntity.ok(ApiResponse.success(subscription));
    }

    @GetMapping("/merchant/{merchantId}/customer/{email}")
    public ResponseEntity<ApiResponse<List<SubscriptionDto>>> getCustomerSubscriptions(
            @PathVariable UUID merchantId, @PathVariable String email) {
        List<SubscriptionDto> subscriptions = subscriptionService.getCustomerSubscriptions(merchantId, email);
        return ResponseEntity.ok(ApiResponse.success(subscriptions));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<SubscriptionDto>> cancelSubscription(@PathVariable UUID id) {
        SubscriptionDto subscription = subscriptionService.cancelSubscription(id);
        return ResponseEntity.ok(ApiResponse.success(subscription, "Subscription set to cancel at period end"));
    }
}

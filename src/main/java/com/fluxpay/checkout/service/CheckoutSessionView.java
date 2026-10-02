package com.fluxpay.checkout.service;

import com.fluxpay.checkout.domain.CheckoutStatus;
import com.fluxpay.common.tenant.Mode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CheckoutSessionView(
        UUID id,
        UUID merchantId,
        Mode mode,
        UUID productId,
        UUID paymentLinkId,
        long amount,
        String currency,
        String customerRef,
        String successUrl,
        String cancelUrl,
        Map<String, String> metadata,
        CheckoutStatus status,
        String gatewayOrderId,
        String url,
        Instant expiresAt,
        Instant completedAt,
        Instant createdAt) {}

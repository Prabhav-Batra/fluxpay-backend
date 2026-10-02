package com.fluxpay.checkout.service;

import com.fluxpay.checkout.domain.CheckoutStatus;
import com.fluxpay.common.tenant.Mode;
import java.time.Instant;
import java.util.UUID;

/** What the hosted checkout page may show. successUrl is present only once the session is completed. */
public record PublicCheckoutView(
        UUID id,
        Mode mode,
        CheckoutStatus status,
        long amount,
        String currency,
        String productName,
        String productDescription,
        String productImageUrl,
        String merchantName,
        String merchantLogoUrl,
        String merchantBrandColor,
        String successUrl,
        String cancelUrl,
        Instant expiresAt) {}

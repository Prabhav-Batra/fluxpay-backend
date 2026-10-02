package com.fluxpay.catalog.service;

import com.fluxpay.common.tenant.Mode;
import java.time.Instant;
import java.util.UUID;

public record PaymentLinkView(
        UUID id,
        UUID merchantId,
        Mode mode,
        UUID productId,
        String slug,
        String url,
        String successUrl,
        String cancelUrl,
        boolean active,
        Instant createdAt) {}

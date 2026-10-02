package com.fluxpay.catalog.api;

import jakarta.validation.constraints.NotBlank;

public record CreatePaymentLinkRequest(@NotBlank String productId, String successUrl, String cancelUrl) {}

package com.fluxpay.checkout.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateCheckoutSessionRequest(
        @NotBlank String productId,
        @Size(max = 255) String customerRef,
        @NotBlank @Size(max = 2048) String successUrl,
        @NotBlank @Size(max = 2048) String cancelUrl,
        Map<String, String> metadata) {}

package com.fluxpay.checkout.service;

import java.util.Map;
import java.util.UUID;

public record NewCheckoutSession(
        UUID productId, String customerRef, String successUrl, String cancelUrl, Map<String, String> metadata) {}

package com.fluxpay.checkout.service;

/** Everything the browser needs to open the gateway's checkout for an order. */
public record PaymentInstructions(
        String gateway,
        String keyId,
        String orderId,
        long amount,
        String currency,
        String merchantName,
        String productName) {}

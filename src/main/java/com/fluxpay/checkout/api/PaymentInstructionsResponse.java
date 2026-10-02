package com.fluxpay.checkout.api;

import com.fluxpay.checkout.service.PaymentInstructions;

public record PaymentInstructionsResponse(
        String gateway,
        String keyId,
        String orderId,
        long amount,
        String currency,
        String merchantName,
        String productName) {

    public static PaymentInstructionsResponse from(PaymentInstructions instructions) {
        return new PaymentInstructionsResponse(
                instructions.gateway(),
                instructions.keyId(),
                instructions.orderId(),
                instructions.amount(),
                instructions.currency(),
                instructions.merchantName(),
                instructions.productName());
    }
}

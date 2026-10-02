package com.fluxpay.payments.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.domain.Payment;
import com.fluxpay.payments.domain.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

public record RecordedPayment(
        UUID id,
        UUID merchantId,
        Mode mode,
        UUID checkoutSessionId,
        String gatewayPaymentId,
        PaymentStatus status,
        long amount,
        String currency,
        String method,
        long gatewayFee,
        Instant createdAt) {

    static RecordedPayment from(Payment payment) {
        return new RecordedPayment(
                payment.getId(),
                payment.getMerchantId(),
                payment.getMode(),
                payment.getCheckoutSessionId(),
                payment.getGatewayPaymentId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMethod(),
                payment.getGatewayFee(),
                payment.getCreatedAt());
    }
}

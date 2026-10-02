package com.fluxpay.payments.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.domain.PaymentStatus;
import java.util.UUID;

public record NewPaymentRecord(
        UUID merchantId,
        Mode mode,
        UUID checkoutSessionId,
        String gateway,
        GatewayPayment payment,
        PaymentStatus status) {}

package com.fluxpay.payments.service;

import com.fluxpay.common.tenant.Mode;
import java.util.List;
import java.util.Map;

/** A payment provider. Razorpay today; Razorpay Route or others later (ADR-002). */
public interface PaymentGateway {

    String name();

    boolean isEnabled(Mode mode);

    /** The key the browser checkout script needs. GATEWAY_NOT_CONFIGURED when the mode has no credentials. */
    String publicKeyId(Mode mode);

    GatewayOrder createOrder(Mode mode, long amount, String currency, String receipt, Map<String, String> notes);

    List<GatewayPayment> fetchOrderPayments(Mode mode, String orderId);

    /** False for a wrong, missing or unverifiable signature. Never throws. */
    boolean verifyWebhookSignature(Mode mode, byte[] body, String signature);

    /** False for a wrong, missing or unverifiable payment signature. Never throws. */
    boolean verifyPaymentSignature(Mode mode, String orderId, String paymentId, String signature);
}

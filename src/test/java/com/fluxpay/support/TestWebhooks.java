package com.fluxpay.support;

import com.fluxpay.payments.service.WebhookSignatures;
import java.nio.charset.StandardCharsets;

/** Razorpay-shaped webhook bodies signed with {@link FakePaymentGateway#WEBHOOK_SECRET}. */
public final class TestWebhooks {

    private TestWebhooks() {}

    public static String paymentCaptured(String paymentId, String orderId, long amount, long fee) {
        return """
                {"entity":"event","event":"payment.captured","contains":["payment"],"payload":{"payment":{"entity":\
                {"id":"%s","entity":"payment","order_id":"%s","amount":%d,"currency":"INR","status":"captured",\
                "method":"upi","fee":%d,"tax":0}}},"created_at":1759363200}"""
                .formatted(paymentId, orderId, amount, fee);
    }

    public static String refundProcessed(String refundId, String paymentId, long amount) {
        return """
                {"entity":"event","event":"refund.processed","contains":["refund","payment"],"payload":{"refund":\
                {"entity":{"id":"%s","entity":"refund","payment_id":"%s","amount":%d,"currency":"INR",\
                "status":"processed"}}},"created_at":1759363200}"""
                .formatted(refundId, paymentId, amount);
    }

    public static String sign(String body) {
        return WebhookSignatures.hmacSha256Hex(
                FakePaymentGateway.WEBHOOK_SECRET, body.getBytes(StandardCharsets.UTF_8));
    }
}

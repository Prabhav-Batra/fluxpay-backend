package com.fluxpay.external.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies Razorpay signatures (HMAC-SHA256, lowercase hex).
 * <ul>
 *   <li>Checkout callback: HMAC(order_id + "|" + payment_id, key_secret)</li>
 *   <li>Webhook: HMAC(raw request body, webhook_secret) from the X-Razorpay-Signature header</li>
 * </ul>
 */
@Component
public class RazorpaySignatureVerifier {

    @Value("${fluxpay.gateways.razorpay.key-secret:}")
    private String keySecret;

    @Value("${fluxpay.gateways.razorpay.webhook-secret:}")
    private String webhookSecret;

    public boolean isValidPaymentSignature(String razorpayOrderId, String razorpayPaymentId, String signature) {
        if (keySecret.isBlank()) {
            return false;
        }
        return matches(hmacHex(razorpayOrderId + "|" + razorpayPaymentId, keySecret), signature);
    }

    public boolean isWebhookConfigured() {
        return !webhookSecret.isBlank();
    }

    public boolean isValidWebhookSignature(String rawBody, String signature) {
        if (!isWebhookConfigured()) {
            return false;
        }
        return matches(hmacHex(rawBody, webhookSecret), signature);
    }

    private static boolean matches(String expected, String provided) {
        if (provided == null) {
            return false;
        }
        // Constant-time comparison so the signature can't be guessed byte by byte
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacHex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}

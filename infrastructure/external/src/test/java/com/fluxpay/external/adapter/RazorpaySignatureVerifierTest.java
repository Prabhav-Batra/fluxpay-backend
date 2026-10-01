package com.fluxpay.external.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RazorpaySignatureVerifierTest {

    // Reference values computed independently: hmac.new(key, msg, sha256).hexdigest()
    private static final String PAYMENT_SIGNATURE = "15656b40fea6f2159b578efa459e969de9f5e223fb8a08393e274ac578d9d005";
    private static final String WEBHOOK_BODY = "{\"event\":\"payment.captured\"}";
    private static final String WEBHOOK_SIGNATURE = "016023acef0ec9b7de72e60947e71c4db59259047a0e7345b5f7b848cd84e0bf";

    private RazorpaySignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new RazorpaySignatureVerifier();
        ReflectionTestUtils.setField(verifier, "keySecret", "test_secret");
        ReflectionTestUtils.setField(verifier, "webhookSecret", "wh_secret");
    }

    @Test
    void acceptsValidPaymentSignature() {
        assertTrue(verifier.isValidPaymentSignature("order_ABC", "pay_XYZ", PAYMENT_SIGNATURE));
    }

    @Test
    void rejectsTamperedOrMissingPaymentSignature() {
        assertFalse(verifier.isValidPaymentSignature("order_ABC", "pay_OTHER", PAYMENT_SIGNATURE));
        assertFalse(verifier.isValidPaymentSignature("order_ABC", "pay_XYZ", PAYMENT_SIGNATURE.toUpperCase()));
        assertFalse(verifier.isValidPaymentSignature("order_ABC", "pay_XYZ", null));
    }

    @Test
    void rejectsEverythingWhenSecretNotConfigured() {
        ReflectionTestUtils.setField(verifier, "keySecret", "");
        assertFalse(verifier.isValidPaymentSignature("order_ABC", "pay_XYZ", PAYMENT_SIGNATURE));
    }

    @Test
    void verifiesWebhookSignature() {
        assertTrue(verifier.isValidWebhookSignature(WEBHOOK_BODY, WEBHOOK_SIGNATURE));
        assertFalse(verifier.isValidWebhookSignature(WEBHOOK_BODY + " ", WEBHOOK_SIGNATURE));
    }

    @Test
    void webhookRejectedWhenSecretMissing() {
        ReflectionTestUtils.setField(verifier, "webhookSecret", "");
        assertFalse(verifier.isWebhookConfigured());
        assertFalse(verifier.isValidWebhookSignature(WEBHOOK_BODY, WEBHOOK_SIGNATURE));
    }

    @Test
    void convertsToSubunits() {
        assertEquals(49900, RazorpayAdapter.toSubunits(new BigDecimal("499"), "INR"));
        assertEquals(100, RazorpayAdapter.toSubunits(new BigDecimal("1.00"), "INR"));
        assertEquals(1999, RazorpayAdapter.toSubunits(new BigDecimal("19.99"), "USD"));
        assertEquals(500, RazorpayAdapter.toSubunits(new BigDecimal("500"), "JPY"));
    }
}

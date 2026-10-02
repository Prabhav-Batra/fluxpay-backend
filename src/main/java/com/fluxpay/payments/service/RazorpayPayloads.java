package com.fluxpay.payments.service;

import java.util.Map;

/** Maps Razorpay JSON entities (as maps) to gateway value types. */
final class RazorpayPayloads {

    private RazorpayPayloads() {}

    static GatewayPayment toPayment(Map<?, ?> entity) {
        return new GatewayPayment(
                text(entity, "id"),
                text(entity, "order_id"),
                number(entity, "amount"),
                text(entity, "currency"),
                text(entity, "status"),
                text(entity, "method"),
                entity.get("fee") == null ? 0 : number(entity, "fee"));
    }

    static GatewayRefund toRefund(Map<?, ?> entity) {
        return new GatewayRefund(
                text(entity, "id"), text(entity, "payment_id"), number(entity, "amount"), text(entity, "currency"));
    }

    private static String text(Map<?, ?> entity, String key) {
        Object value = entity.get(key);
        return value == null ? null : value.toString();
    }

    private static long number(Map<?, ?> entity, String key) {
        Object value = entity.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Razorpay field " + key + " is not a number");
        }
        return number.longValue();
    }
}

package com.fluxpay.external.gateway;

/**
 * Result of creating an order/session at a payment gateway.
 *
 * @param clientToken    value the checkout frontend needs to open the gateway UI
 *                       (Razorpay order id, Cashfree payment_session_id, PayU link)
 * @param gatewayOrderId the gateway's identifier for the order, stored on the PaymentIntent
 *                       so later callbacks/webhooks can be matched back to it (may be null)
 */
public record GatewayOrder(String clientToken, String gatewayOrderId) {
}

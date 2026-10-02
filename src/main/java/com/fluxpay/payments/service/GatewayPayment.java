package com.fluxpay.payments.service;

/** A payment as the gateway reports it. {@code fee} includes the gateway's tax; 0 when not reported. */
public record GatewayPayment(
        String id, String orderId, long amount, String currency, String status, String method, long fee) {

    public boolean isCaptured() {
        return "captured".equals(status);
    }
}

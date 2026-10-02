package com.fluxpay.payments.service;

public sealed interface GatewayWebhookEvent {

    record PaymentCaptured(GatewayPayment payment) implements GatewayWebhookEvent {}

    record RefundProcessed(GatewayRefund refund) implements GatewayWebhookEvent {}

    record Ignored(String type) implements GatewayWebhookEvent {}
}

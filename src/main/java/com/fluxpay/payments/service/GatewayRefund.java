package com.fluxpay.payments.service;

public record GatewayRefund(String id, String paymentId, long amount, String currency) {}

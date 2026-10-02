package com.fluxpay.payments.service;

public record GatewayOrder(String id, long amount, String currency) {}

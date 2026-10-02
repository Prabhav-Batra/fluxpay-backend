package com.fluxpay.catalog.api;

public record UpdatePaymentLinkRequest(String successUrl, String cancelUrl, Boolean active) {}

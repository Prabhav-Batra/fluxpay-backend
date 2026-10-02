package com.fluxpay.catalog.service;

/** Null leaves a field unchanged; an empty URL clears it. */
public record PaymentLinkChanges(String successUrl, String cancelUrl, Boolean active) {}

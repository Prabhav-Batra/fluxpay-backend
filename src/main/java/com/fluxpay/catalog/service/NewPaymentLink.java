package com.fluxpay.catalog.service;

import java.util.UUID;

public record NewPaymentLink(UUID productId, String successUrl, String cancelUrl) {}

package com.fluxpay.ledger.service;

public record SaleEntries(long platformFee, long gatewayFee, long net) {}

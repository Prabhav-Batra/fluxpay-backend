package com.fluxpay.ledger.service;

public record Balance(
        long grossSales,
        long platformFees,
        long gatewayFees,
        long refunds,
        long payouts,
        long available,
        String currency) {}

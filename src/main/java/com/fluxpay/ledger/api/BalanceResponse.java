package com.fluxpay.ledger.api;

import com.fluxpay.ledger.service.Balance;

public record BalanceResponse(
        long grossSales,
        long platformFees,
        long gatewayFees,
        long refunds,
        long payouts,
        long available,
        String currency) {

    public static BalanceResponse from(Balance balance) {
        return new BalanceResponse(
                balance.grossSales(),
                balance.platformFees(),
                balance.gatewayFees(),
                balance.refunds(),
                balance.payouts(),
                balance.available(),
                balance.currency());
    }
}

package com.fluxpay.ledger.service;

public final class FeeCalculator {

    private static final long BPS_DENOMINATOR = 10_000;

    private FeeCalculator() {}

    /** gross × bps / 10000, rounded half up. Inputs are non-negative. */
    public static long platformFee(long gross, int bps) {
        return (gross * bps + BPS_DENOMINATOR / 2) / BPS_DENOMINATOR;
    }
}

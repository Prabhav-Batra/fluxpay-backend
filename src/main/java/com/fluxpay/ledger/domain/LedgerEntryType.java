package com.fluxpay.ledger.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum LedgerEntryType {
    SALE_GROSS,
    PLATFORM_FEE,
    GATEWAY_FEE,
    REFUND,
    PAYOUT;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}

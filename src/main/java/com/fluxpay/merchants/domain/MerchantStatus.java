package com.fluxpay.merchants.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum MerchantStatus {
    ACTIVE,
    SUSPENDED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}

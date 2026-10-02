package com.fluxpay.checkout.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum CheckoutStatus {
    OPEN,
    COMPLETED,
    EXPIRED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}

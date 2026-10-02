package com.fluxpay.events.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum DeliveryStatus {
    PENDING,
    SUCCEEDED,
    FAILED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}

package com.fluxpay.sales.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum SaleStatus {
    PAID,
    PARTIALLY_REFUNDED,
    REFUNDED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<SaleStatus> parse(String raw) {
        return Arrays.stream(values())
                .filter(status -> status.value().equals(raw))
                .findFirst();
    }
}

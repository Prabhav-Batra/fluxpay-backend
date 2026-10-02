package com.fluxpay.catalog.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum ProductType {
    ONE_TIME;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}

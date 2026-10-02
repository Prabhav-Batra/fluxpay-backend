package com.fluxpay.identity.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum Role {
    MERCHANT_OWNER,
    PLATFORM_ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}

package com.fluxpay.common.tenant;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

public enum Mode {
    TEST("test"),
    LIVE("live");

    private final String value;

    Mode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static Optional<Mode> parse(String raw) {
        return Arrays.stream(values()).filter(mode -> mode.value.equals(raw)).findFirst();
    }
}

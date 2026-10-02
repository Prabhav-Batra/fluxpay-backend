package com.fluxpay.events.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EventType {
    CHECKOUT_COMPLETED("checkout.completed"),
    CHECKOUT_EXPIRED("checkout.expired"),
    SALE_REFUNDED("sale.refunded");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}

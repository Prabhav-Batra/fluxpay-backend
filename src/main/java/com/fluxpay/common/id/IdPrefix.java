package com.fluxpay.common.id;

public enum IdPrefix {
    MERCHANT("acct"),
    USER("user"),
    API_KEY("key"),
    PRODUCT("prod"),
    PAYMENT_LINK("plink"),
    CHECKOUT_SESSION("cs"),
    PAYMENT("pay"),
    SALE("sale"),
    EVENT("evt"),
    WEBHOOK_ENDPOINT("we"),
    PAYOUT("po");

    private final String value;

    IdPrefix(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}

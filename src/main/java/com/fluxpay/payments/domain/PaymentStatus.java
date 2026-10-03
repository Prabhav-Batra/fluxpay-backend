package com.fluxpay.payments.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** CAPTURED produced a sale; AMOUNT_MISMATCH and DUPLICATE need manual review and refund. */
public enum PaymentStatus {
    CAPTURED,
    AMOUNT_MISMATCH,
    DUPLICATE,
    FAILED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}

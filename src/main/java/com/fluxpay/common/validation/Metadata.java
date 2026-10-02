package com.fluxpay.common.validation;

import com.fluxpay.common.error.FluxpayException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Free-form string key/value pairs merchants attach to products and checkout sessions. */
public final class Metadata {

    static final int MAX_KEYS = 20;
    static final int MAX_VALUE_LENGTH = 500;
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.-]{1,40}");

    private Metadata() {}

    public static Map<String, String> validated(Map<String, String> metadata) {
        if (metadata == null) {
            return new LinkedHashMap<>();
        }
        if (metadata.size() > MAX_KEYS) {
            throw FluxpayException.validation(
                    "metadata", "METADATA_TOO_MANY_KEYS", "metadata may have at most " + MAX_KEYS + " keys");
        }
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (entry.getKey() == null || !KEY.matcher(entry.getKey()).matches()) {
                throw FluxpayException.validation(
                        "metadata",
                        "METADATA_INVALID_KEY",
                        "metadata keys must be 1-40 letters, digits, '_', '-' or '.'");
            }
            if (entry.getValue() == null || entry.getValue().length() > MAX_VALUE_LENGTH) {
                throw FluxpayException.validation(
                        "metadata",
                        "METADATA_INVALID_VALUE",
                        "metadata values must be strings of at most " + MAX_VALUE_LENGTH + " characters");
            }
        }
        return new LinkedHashMap<>(metadata);
    }
}

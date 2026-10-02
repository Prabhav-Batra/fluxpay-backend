package com.fluxpay.identity.service;

import com.fluxpay.common.error.FluxpayException;
import java.nio.charset.StandardCharsets;

/** bcrypt ignores input beyond 72 bytes, so longer passwords are rejected rather than silently truncated. */
public final class PasswordPolicy {

    static final int MIN_LENGTH = 10;
    static final int MAX_BYTES = 72;

    private PasswordPolicy() {}

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw FluxpayException.validation(
                    "password", "PASSWORD_TOO_SHORT", "Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw FluxpayException.validation(
                    "password", "PASSWORD_TOO_LONG", "Password must be at most " + MAX_BYTES + " bytes");
        }
    }
}

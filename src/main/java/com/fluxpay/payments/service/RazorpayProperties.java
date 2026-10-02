package com.fluxpay.payments.service;

import com.fluxpay.common.tenant.Mode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Test credentials are required at startup; live credentials are optional but must be complete if present. */
@Validated
@ConfigurationProperties("fluxpay.razorpay")
public record RazorpayProperties(
        @NotBlank String baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Valid @NotNull Credentials test,
        Credentials live) {

    public RazorpayProperties {
        if (live != null && !live.isBlank() && !live.isComplete()) {
            throw new IllegalArgumentException(
                    "fluxpay.razorpay.live is partially configured: set key-id, key-secret and webhook-secret");
        }
    }

    public Optional<Credentials> credentials(Mode mode) {
        Credentials credentials = mode == Mode.TEST ? test : live;
        return credentials != null && credentials.isComplete() ? Optional.of(credentials) : Optional.empty();
    }

    public record Credentials(@NotBlank String keyId, @NotBlank String keySecret, @NotBlank String webhookSecret) {

        boolean isBlank() {
            return blank(keyId) && blank(keySecret) && blank(webhookSecret);
        }

        boolean isComplete() {
            return !blank(keyId) && !blank(keySecret) && !blank(webhookSecret);
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }

        @Override
        public String toString() {
            return "Credentials[keyId=" + keyId + ", secrets=redacted]";
        }
    }
}

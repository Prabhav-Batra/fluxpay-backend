package com.fluxpay.common.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("fluxpay.jobs")
public record JobsProperties(
        boolean enabled,
        @NotNull Duration expiryInterval,
        @NotNull Duration reconciliationInterval,
        @NotNull Duration reconciliationMinAge,
        @NotNull Duration reconciliationGraceAfterExpiry,
        @NotNull Duration idempotencyRetention) {}

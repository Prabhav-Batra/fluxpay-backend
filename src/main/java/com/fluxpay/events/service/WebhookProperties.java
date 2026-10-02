package com.fluxpay.events.service;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code allowLoopback} permits test-mode endpoints on localhost; keep it false in production. */
@Validated
@ConfigurationProperties("fluxpay.webhooks")
public record WebhookProperties(
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration lease,
        @Min(1) int batchSize,
        boolean allowLoopback) {}

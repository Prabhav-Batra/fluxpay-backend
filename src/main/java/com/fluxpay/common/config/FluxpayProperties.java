package com.fluxpay.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Platform-wide settings. The app refuses to start when a required value is missing. */
@Validated
@ConfigurationProperties("fluxpay")
public record FluxpayProperties(@NotBlank String frontendBaseUrl) {}

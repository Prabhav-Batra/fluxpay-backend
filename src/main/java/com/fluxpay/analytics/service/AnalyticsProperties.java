package com.fluxpay.analytics.service;

import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Day boundaries for analytics are computed in this zone (merchants are in India by default). */
@Validated
@ConfigurationProperties("fluxpay.analytics")
public record AnalyticsProperties(@NotNull ZoneId timezone) {}

package com.fluxpay.checkout.service;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("fluxpay.checkout")
public record CheckoutProperties(@NotNull Duration sessionTtl) {}

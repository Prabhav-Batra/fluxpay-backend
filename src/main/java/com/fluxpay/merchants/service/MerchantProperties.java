package com.fluxpay.merchants.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("fluxpay.merchants")
public record MerchantProperties(@Min(0) @Max(10000) int defaultPlatformFeeBps) {}

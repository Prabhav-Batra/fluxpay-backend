package com.fluxpay.identity.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Optional: when both are set, the platform admin is created on startup if missing. */
@ConfigurationProperties("fluxpay.admin")
public record AdminBootstrapProperties(String bootstrapEmail, String bootstrapPassword) {

    public boolean isConfigured() {
        return bootstrapEmail != null
                && !bootstrapEmail.isBlank()
                && bootstrapPassword != null
                && !bootstrapPassword.isBlank();
    }
}

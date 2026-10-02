package com.fluxpay.events.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.events.domain.WebhookEndpoint;
import java.time.Instant;
import java.util.UUID;

public record WebhookEndpointView(UUID id, Mode mode, String url, boolean enabled, Instant createdAt) {

    static WebhookEndpointView from(WebhookEndpoint endpoint) {
        return new WebhookEndpointView(
                endpoint.getId(), endpoint.getMode(), endpoint.getUrl(), endpoint.isEnabled(), endpoint.getCreatedAt());
    }
}

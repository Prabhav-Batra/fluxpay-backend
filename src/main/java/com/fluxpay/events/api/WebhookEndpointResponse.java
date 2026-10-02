package com.fluxpay.events.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.events.service.IssuedWebhookEndpoint;
import com.fluxpay.events.service.WebhookEndpointView;
import java.time.Instant;

public record WebhookEndpointResponse(
        String id,
        Mode mode,
        String url,
        boolean enabled,
        Instant createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String secret) {

    public static WebhookEndpointResponse from(WebhookEndpointView view) {
        return new WebhookEndpointResponse(
                PublicId.of(IdPrefix.WEBHOOK_ENDPOINT, view.id()),
                view.mode(),
                view.url(),
                view.enabled(),
                view.createdAt(),
                null);
    }

    public static WebhookEndpointResponse from(IssuedWebhookEndpoint issued) {
        WebhookEndpointView view = issued.endpoint();
        return new WebhookEndpointResponse(
                PublicId.of(IdPrefix.WEBHOOK_ENDPOINT, view.id()),
                view.mode(),
                view.url(),
                view.enabled(),
                view.createdAt(),
                issued.secret());
    }
}

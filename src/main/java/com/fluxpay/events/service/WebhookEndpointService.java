package com.fluxpay.events.service;

import com.fluxpay.common.tenant.TenantContext;
import java.util.List;
import java.util.UUID;

public interface WebhookEndpointService {

    List<WebhookEndpointView> list(TenantContext tenant);

    /** ENDPOINT_LIMIT_REACHED (422) past the per-mode limit; URL rules per {@link WebhookUrlPolicy}. */
    IssuedWebhookEndpoint create(TenantContext tenant, String url);

    WebhookEndpointView update(TenantContext tenant, UUID endpointId, String url, Boolean enabled);

    IssuedWebhookEndpoint rollSecret(TenantContext tenant, UUID endpointId);

    void delete(TenantContext tenant, UUID endpointId);

    /** Queues a webhook.test event for this endpoint only. */
    EventView sendTest(TenantContext tenant, UUID endpointId);
}

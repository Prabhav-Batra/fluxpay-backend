package com.fluxpay.events.service;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.Base62;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.events.domain.Event;
import com.fluxpay.events.domain.EventType;
import com.fluxpay.events.domain.WebhookDelivery;
import com.fluxpay.events.domain.WebhookEndpoint;
import com.fluxpay.events.persistence.EventRepository;
import com.fluxpay.events.persistence.WebhookDeliveryRepository;
import com.fluxpay.events.persistence.WebhookEndpointRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookEndpointServiceImpl implements WebhookEndpointService {

    static final int MAX_ENDPOINTS_PER_MODE = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebhookEndpointRepository endpoints;
    private final EventRepository events;
    private final WebhookDeliveryRepository deliveries;
    private final Clock clock;

    public WebhookEndpointServiceImpl(
            WebhookEndpointRepository endpoints,
            EventRepository events,
            WebhookDeliveryRepository deliveries,
            Clock clock) {
        this.endpoints = endpoints;
        this.events = events;
        this.deliveries = deliveries;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebhookEndpointView> list(TenantContext tenant) {
        return endpoints
                .findByMerchantIdAndModeAndDeletedAtIsNullOrderByCreatedAtAsc(tenant.merchantId(), tenant.mode())
                .stream()
                .map(WebhookEndpointView::from)
                .toList();
    }

    @Override
    @Transactional
    public IssuedWebhookEndpoint create(TenantContext tenant, String url) {
        WebhookUrlPolicy.validate(tenant.mode(), url);
        if (endpoints.countByMerchantIdAndModeAndDeletedAtIsNull(tenant.merchantId(), tenant.mode())
                >= MAX_ENDPOINTS_PER_MODE) {
            throw FluxpayException.validation(
                    "url", "ENDPOINT_LIMIT_REACHED", "At most " + MAX_ENDPOINTS_PER_MODE + " endpoints per mode");
        }
        String secret = newSecret();
        WebhookEndpoint endpoint = endpoints.save(
                new WebhookEndpoint(tenant.merchantId(), tenant.mode(), url, secret, Instant.now(clock)));
        return new IssuedWebhookEndpoint(WebhookEndpointView.from(endpoint), secret);
    }

    @Override
    @Transactional
    public WebhookEndpointView update(TenantContext tenant, UUID endpointId, String url, Boolean enabled) {
        WebhookEndpoint endpoint = find(tenant, endpointId);
        Instant now = Instant.now(clock);
        if (url != null) {
            WebhookUrlPolicy.validate(tenant.mode(), url);
            endpoint.changeUrl(url, now);
        }
        if (enabled != null) {
            endpoint.setEnabled(enabled, now);
        }
        return WebhookEndpointView.from(endpoint);
    }

    @Override
    @Transactional
    public IssuedWebhookEndpoint rollSecret(TenantContext tenant, UUID endpointId) {
        WebhookEndpoint endpoint = find(tenant, endpointId);
        String secret = newSecret();
        endpoint.rollSecret(secret, Instant.now(clock));
        return new IssuedWebhookEndpoint(WebhookEndpointView.from(endpoint), secret);
    }

    @Override
    @Transactional
    public void delete(TenantContext tenant, UUID endpointId) {
        find(tenant, endpointId).delete(Instant.now(clock));
    }

    @Override
    @Transactional
    public EventView sendTest(TenantContext tenant, UUID endpointId) {
        WebhookEndpoint endpoint = find(tenant, endpointId);
        Instant now = Instant.now(clock);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "FluxPay test event");
        data.put("endpoint_id", PublicId.of(IdPrefix.WEBHOOK_ENDPOINT, endpoint.getId()));
        Event event = events.save(new Event(tenant.merchantId(), tenant.mode(), EventType.WEBHOOK_TEST, data, now));
        deliveries.save(new WebhookDelivery(event.getId(), endpoint.getId(), tenant.merchantId(), tenant.mode(), now));
        return EventView.from(event);
    }

    private WebhookEndpoint find(TenantContext tenant, UUID endpointId) {
        return endpoints
                .findByIdAndMerchantIdAndModeAndDeletedAtIsNull(endpointId, tenant.merchantId(), tenant.mode())
                .orElseThrow(
                        () -> FluxpayException.notFound("WEBHOOK_ENDPOINT_NOT_FOUND", "Webhook endpoint not found"));
    }

    private static String newSecret() {
        return "whsec_" + Base62.random(32, RANDOM);
    }
}

package com.fluxpay.events.service;

import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.events.domain.Event;
import com.fluxpay.events.domain.EventType;
import com.fluxpay.events.domain.WebhookDelivery;
import com.fluxpay.events.domain.WebhookEndpoint;
import com.fluxpay.events.persistence.EventRepository;
import com.fluxpay.events.persistence.WebhookDeliveryRepository;
import com.fluxpay.events.persistence.WebhookEndpointRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventPublisherImpl implements EventPublisher {

    private final EventRepository events;
    private final WebhookEndpointRepository endpoints;
    private final WebhookDeliveryRepository deliveries;
    private final Clock clock;

    public EventPublisherImpl(
            EventRepository events,
            WebhookEndpointRepository endpoints,
            WebhookDeliveryRepository deliveries,
            Clock clock) {
        this.events = events;
        this.endpoints = endpoints;
        this.deliveries = deliveries;
        this.clock = clock;
    }

    /** Writes the event and one pending delivery per enabled endpoint, all in the caller's transaction. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public EventView publish(TenantContext tenant, EventType type, Map<String, Object> data) {
        Instant now = Instant.now(clock);
        Event event = events.save(new Event(tenant.merchantId(), tenant.mode(), type, new LinkedHashMap<>(data), now));
        for (WebhookEndpoint endpoint :
                endpoints.findByMerchantIdAndModeAndEnabledTrueAndDeletedAtIsNull(tenant.merchantId(), tenant.mode())) {
            deliveries.save(
                    new WebhookDelivery(event.getId(), endpoint.getId(), tenant.merchantId(), tenant.mode(), now));
        }
        return EventView.from(event);
    }
}

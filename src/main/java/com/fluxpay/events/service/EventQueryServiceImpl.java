package com.fluxpay.events.service;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.events.domain.Event;
import com.fluxpay.events.domain.WebhookDelivery;
import com.fluxpay.events.domain.WebhookEndpoint;
import com.fluxpay.events.persistence.EventRepository;
import com.fluxpay.events.persistence.WebhookDeliveryRepository;
import com.fluxpay.events.persistence.WebhookEndpointRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventQueryServiceImpl implements EventQueryService {

    private final EventRepository events;
    private final WebhookEndpointRepository endpoints;
    private final WebhookDeliveryRepository deliveries;
    private final Clock clock;

    public EventQueryServiceImpl(
            EventRepository events,
            WebhookEndpointRepository endpoints,
            WebhookDeliveryRepository deliveries,
            Clock clock) {
        this.events = events;
        this.endpoints = endpoints;
        this.deliveries = deliveries;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<EventView> list(TenantContext tenant, PageQuery query) {
        return CursorPage.from(
                events.page(tenant.merchantId(), tenant.mode(), query.before(), Limit.of(query.fetchSize())),
                query,
                EventView::from,
                view -> PublicId.of(IdPrefix.EVENT, view.id()));
    }

    @Override
    @Transactional(readOnly = true)
    public EventDetailView get(TenantContext tenant, UUID eventId) {
        Event event = find(tenant, eventId);
        List<DeliveryView> attempts = deliveries.findByEventIdOrderByCreatedAtAsc(event.getId()).stream()
                .map(DeliveryView::from)
                .toList();
        return new EventDetailView(EventView.from(event), attempts);
    }

    @Override
    @Transactional
    public int resend(TenantContext tenant, UUID eventId) {
        Event event = find(tenant, eventId);
        Instant now = Instant.now(clock);
        List<WebhookEndpoint> targets =
                endpoints.findByMerchantIdAndModeAndEnabledTrueAndDeletedAtIsNull(tenant.merchantId(), tenant.mode());
        targets.forEach(endpoint -> deliveries.save(
                new WebhookDelivery(event.getId(), endpoint.getId(), tenant.merchantId(), tenant.mode(), now)));
        return targets.size();
    }

    private Event find(TenantContext tenant, UUID eventId) {
        return events.findByIdAndMerchantIdAndMode(eventId, tenant.merchantId(), tenant.mode())
                .orElseThrow(() -> FluxpayException.notFound("EVENT_NOT_FOUND", "Event not found"));
    }
}

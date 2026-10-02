package com.fluxpay.events.service;

import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.events.domain.Event;
import com.fluxpay.events.domain.EventType;
import com.fluxpay.events.persistence.EventRepository;
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
    private final Clock clock;

    public EventPublisherImpl(EventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public EventView publish(TenantContext tenant, EventType type, Map<String, Object> data) {
        Event event = events.save(
                new Event(tenant.merchantId(), tenant.mode(), type, new LinkedHashMap<>(data), Instant.now(clock)));
        return new EventView(
                event.getId(),
                event.getMerchantId(),
                event.getMode(),
                event.getType(),
                event.getData(),
                event.getCreatedAt());
    }
}

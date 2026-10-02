package com.fluxpay.events.service;

import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import java.util.UUID;

public interface EventQueryService {

    CursorPage<EventView> list(TenantContext tenant, PageQuery query);

    /** EVENT_NOT_FOUND (404) unless the event belongs to this tenant and mode. */
    EventDetailView get(TenantContext tenant, UUID eventId);

    /** Queues a new delivery of the event to every enabled endpoint. Returns how many were queued. */
    int resend(TenantContext tenant, UUID eventId);
}

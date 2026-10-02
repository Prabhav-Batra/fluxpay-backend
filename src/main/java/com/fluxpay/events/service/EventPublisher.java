package com.fluxpay.events.service;

import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.events.domain.EventType;
import java.util.Map;

public interface EventPublisher {

    /**
     * Writes the event to the outbox in the caller's transaction, so an event exists if and only if the
     * change that caused it commits. Throws IllegalTransactionStateException when no transaction is active.
     */
    EventView publish(TenantContext tenant, EventType type, Map<String, Object> data);
}

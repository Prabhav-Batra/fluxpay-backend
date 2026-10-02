package com.fluxpay.events.api;

import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.events.domain.EventType;
import com.fluxpay.events.service.EventView;
import java.time.Instant;
import java.util.Map;

public record EventResponse(String id, EventType type, Mode mode, Map<String, Object> data, Instant createdAt) {

    public static EventResponse from(EventView view) {
        return new EventResponse(
                PublicId.of(IdPrefix.EVENT, view.id()), view.type(), view.mode(), view.data(), view.createdAt());
    }
}

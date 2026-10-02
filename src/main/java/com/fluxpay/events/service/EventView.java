package com.fluxpay.events.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.events.domain.EventType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventView(
        UUID id, UUID merchantId, Mode mode, EventType type, Map<String, Object> data, Instant createdAt) {}

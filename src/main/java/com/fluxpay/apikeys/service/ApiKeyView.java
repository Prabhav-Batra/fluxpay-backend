package com.fluxpay.apikeys.service;

import com.fluxpay.apikeys.domain.ApiKey;
import com.fluxpay.common.tenant.Mode;
import java.time.Instant;
import java.util.UUID;

public record ApiKeyView(
        UUID id, String displayPrefix, Mode mode, Instant createdAt, Instant lastUsedAt, Instant revokedAt) {

    static ApiKeyView from(ApiKey key) {
        return new ApiKeyView(
                key.getId(),
                ApiKeySecret.prefix(key.getMode()) + key.getLookupId(),
                key.getMode(),
                key.getCreatedAt(),
                key.getLastUsedAt(),
                key.getRevokedAt());
    }
}

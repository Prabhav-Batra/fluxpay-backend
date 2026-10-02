package com.fluxpay.apikeys.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fluxpay.apikeys.service.ApiKeyView;
import com.fluxpay.apikeys.service.IssuedApiKey;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import java.time.Instant;

public record ApiKeyResponse(
        String id,
        String displayPrefix,
        Mode mode,
        Instant createdAt,
        Instant lastUsedAt,
        Instant revokedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String secret) {

    public static ApiKeyResponse from(ApiKeyView view) {
        return new ApiKeyResponse(
                PublicId.of(IdPrefix.API_KEY, view.id()),
                view.displayPrefix(),
                view.mode(),
                view.createdAt(),
                view.lastUsedAt(),
                view.revokedAt(),
                null);
    }

    public static ApiKeyResponse from(IssuedApiKey issued) {
        ApiKeyResponse base = from(issued.key());
        return new ApiKeyResponse(
                base.id(),
                base.displayPrefix(),
                base.mode(),
                base.createdAt(),
                base.lastUsedAt(),
                base.revokedAt(),
                issued.secret());
    }
}

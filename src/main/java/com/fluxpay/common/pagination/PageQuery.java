package com.fluxpay.common.pagination;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import java.util.UUID;

/** Cursor pagination over UUIDv7 ids (newest first). {@link #before()} feeds {@code id < :before} queries. */
public record PageQuery(UUID startingAfter, int limit) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;
    private static final UUID MAX_UUID = new UUID(-1L, -1L);

    public static PageQuery of(IdPrefix prefix, String startingAfter, Integer limit) {
        int resolvedLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (resolvedLimit < 1 || resolvedLimit > MAX_LIMIT) {
            throw FluxpayException.badRequest("INVALID_LIMIT", "limit must be between 1 and " + MAX_LIMIT);
        }
        if (startingAfter == null || startingAfter.isEmpty()) {
            return new PageQuery(null, resolvedLimit);
        }
        UUID id = PublicId.parse(prefix, startingAfter)
                .orElseThrow(() -> FluxpayException.badRequest(
                        "INVALID_CURSOR", "starting_after must be a " + prefix.value() + "_ id"));
        return new PageQuery(id, resolvedLimit);
    }

    public UUID before() {
        return startingAfter == null ? MAX_UUID : startingAfter;
    }

    public int fetchSize() {
        return limit + 1;
    }
}

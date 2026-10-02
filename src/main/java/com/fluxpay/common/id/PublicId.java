package com.fluxpay.common.id;

import com.fluxpay.common.error.FluxpayException;
import java.util.Optional;
import java.util.UUID;

/** Formats internal UUIDs as typed public IDs such as {@code prod_0F3k...}. */
public final class PublicId {

    private PublicId() {}

    public static String of(IdPrefix prefix, UUID id) {
        return prefix.value() + "_" + Base62.encodeUuid(id);
    }

    public static Optional<UUID> parse(IdPrefix prefix, String publicId) {
        String expected = prefix.value() + "_";
        if (publicId == null || !publicId.startsWith(expected)) {
            return Optional.empty();
        }
        return Base62.decodeUuid(publicId.substring(expected.length()));
    }

    public static UUID parseOrNotFound(IdPrefix prefix, String publicId, String code, String message) {
        return parse(prefix, publicId).orElseThrow(() -> FluxpayException.notFound(code, message));
    }
}

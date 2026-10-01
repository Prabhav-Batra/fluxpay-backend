package com.fluxpay.shared.security;

import com.fluxpay.shared.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/**
 * The merchant id from the caller's JWT (set by JwtAuthenticationFilter).
 * Use it to stop one merchant reading or changing another merchant's data.
 */
public final class CurrentMerchant {

    static final String REQUEST_ATTRIBUTE = "fluxpay.merchantId";

    private CurrentMerchant() {
    }

    public static UUID require(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        if (value == null) {
            throw new ForbiddenException("No merchant in token");
        }
        return UUID.fromString(value.toString());
    }

    public static void assertIs(HttpServletRequest request, UUID merchantId) {
        if (!require(request).equals(merchantId)) {
            throw new ForbiddenException("Not allowed to access another merchant's resources");
        }
    }
}

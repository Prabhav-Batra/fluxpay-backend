package com.fluxpay.common.error;

/** Transport-neutral error categories. Only {@link HttpStatusMapper} knows their HTTP status. */
public enum ErrorType {
    BAD_REQUEST,
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    VALIDATION,
    RATE_LIMITED,
    GATEWAY_ERROR,
    INTERNAL
}

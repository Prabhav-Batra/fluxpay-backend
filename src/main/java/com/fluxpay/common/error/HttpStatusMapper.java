package com.fluxpay.common.error;

import org.springframework.http.HttpStatus;

public final class HttpStatusMapper {

    private HttpStatusMapper() {}

    public static HttpStatus toStatus(ErrorType type) {
        return switch (type) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case VALIDATION -> HttpStatus.UNPROCESSABLE_ENTITY;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case GATEWAY_ERROR -> HttpStatus.BAD_GATEWAY;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}

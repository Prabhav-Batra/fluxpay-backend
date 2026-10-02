package com.fluxpay.common.error;

import java.util.List;

/** The only exception services throw for expected failures. */
public class FluxpayException extends RuntimeException {

    private final ErrorType type;
    private final String code;
    private final List<ErrorDetail> details;

    public FluxpayException(ErrorType type, String code, String message) {
        this(type, code, message, List.of());
    }

    public FluxpayException(ErrorType type, String code, String message, List<ErrorDetail> details) {
        super(message);
        this.type = type;
        this.code = code;
        this.details = List.copyOf(details);
    }

    public static FluxpayException badRequest(String code, String message) {
        return new FluxpayException(ErrorType.BAD_REQUEST, code, message);
    }

    public static FluxpayException unauthenticated(String code, String message) {
        return new FluxpayException(ErrorType.UNAUTHENTICATED, code, message);
    }

    public static FluxpayException forbidden(String code, String message) {
        return new FluxpayException(ErrorType.FORBIDDEN, code, message);
    }

    public static FluxpayException notFound(String code, String message) {
        return new FluxpayException(ErrorType.NOT_FOUND, code, message);
    }

    public static FluxpayException conflict(String code, String message) {
        return new FluxpayException(ErrorType.CONFLICT, code, message);
    }

    public static FluxpayException validation(String field, String code, String message) {
        return new FluxpayException(
                ErrorType.VALIDATION, "VALIDATION_FAILED", message, List.of(new ErrorDetail(field, code, message)));
    }

    public static FluxpayException rateLimited(String message) {
        return new FluxpayException(ErrorType.RATE_LIMITED, "RATE_LIMITED", message);
    }

    public ErrorType type() {
        return type;
    }

    public String code() {
        return code;
    }

    public List<ErrorDetail> details() {
        return details;
    }
}

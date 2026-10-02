package com.fluxpay.common.error;

import java.util.List;

public record ErrorResponse(Body error) {

    public record Body(String code, String message, List<ErrorDetail> details, String traceId) {}

    public static ErrorResponse of(String code, String message, List<ErrorDetail> details, String traceId) {
        return new ErrorResponse(new Body(code, message, details, traceId));
    }
}

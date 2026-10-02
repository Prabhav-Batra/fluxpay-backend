package com.fluxpay.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxpay.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Writes the standard error envelope from servlet filters, where controller advice does not apply. */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, FluxpayException exception) throws IOException {
        write(
                response,
                HttpStatusMapper.toStatus(exception.type()),
                exception.code(),
                exception.getMessage(),
                exception.details());
    }

    public void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        write(response, status, code, message, List.of());
    }

    private void write(
            HttpServletResponse response, HttpStatus status, String code, String message, List<ErrorDetail> details)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(code, message, details, MDC.get(CorrelationIdFilter.MDC_KEY));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

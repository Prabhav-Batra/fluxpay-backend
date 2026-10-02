package com.fluxpay.config;

import com.fluxpay.common.error.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseWriter errorWriter;

    public RestAccessDeniedHandler(ErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        if (ex instanceof CsrfException) {
            errorWriter.write(response, HttpStatus.FORBIDDEN, "CSRF_TOKEN_INVALID", "Missing or invalid CSRF token");
            return;
        }
        errorWriter.write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this resource");
    }
}

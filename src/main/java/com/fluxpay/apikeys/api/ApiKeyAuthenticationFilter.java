package com.fluxpay.apikeys.api;

import com.fluxpay.apikeys.service.ApiKeyPrincipal;
import com.fluxpay.apikeys.service.ApiKeyService;
import com.fluxpay.common.error.ErrorResponseWriter;
import com.fluxpay.common.error.FluxpayException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates merchant API calls sent with {@code Authorization: Bearer sk_...}. */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private static final List<String> NON_API_KEY_PREFIXES = List.of(
            "/api/v1/auth/", "/api/v1/dashboard/", "/api/v1/admin/", "/api/v1/public/", "/api/v1/gateway-webhooks/");

    private final ApiKeyService apiKeyService;
    private final ErrorResponseWriter errorWriter;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService, ErrorResponseWriter errorWriter) {
        this.apiKeyService = apiKeyService;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/") || NON_API_KEY_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!header.startsWith(BEARER)) {
            rejectInvalidKey(response);
            return;
        }
        Optional<ApiKeyPrincipal> principal;
        try {
            principal =
                    apiKeyService.authenticate(header.substring(BEARER.length()).trim());
        } catch (FluxpayException e) {
            errorWriter.write(response, e);
            return;
        }
        if (principal.isEmpty()) {
            rejectInvalidKey(response);
            return;
        }
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal.get(), null, List.of(new SimpleGrantedAuthority("ROLE_API_KEY")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    private void rejectInvalidKey(HttpServletResponse response) throws IOException {
        errorWriter.write(response, HttpStatus.UNAUTHORIZED, "INVALID_API_KEY", "Invalid API key");
    }
}

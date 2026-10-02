package com.fluxpay.common.ratelimit;

import com.fluxpay.common.error.ErrorResponseWriter;
import com.fluxpay.common.error.FluxpayException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private record CompiledRule(RateLimitProperties.Rule rule, List<PathPattern> patterns) {}

    private final List<CompiledRule> rules;
    private final RateLimiter limiter = new RateLimiter();
    private final ErrorResponseWriter errorWriter;

    public RateLimitFilter(RateLimitProperties properties, ErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
        this.rules = properties.rules().stream()
                .map(rule -> new CompiledRule(
                        rule,
                        rule.paths().stream()
                                .map(PathPatternParser.defaultInstance::parse)
                                .toList()))
                .toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<CompiledRule> match = findRule(request);
        if (match.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        RateLimitDecision decision =
                limiter.tryConsume(request.getRemoteAddr(), match.get().rule());
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetSeconds()));
        if (!decision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(decision.resetSeconds()));
            errorWriter.write(response, FluxpayException.rateLimited("Too many requests, retry later"));
            return;
        }
        chain.doFilter(request, response);
    }

    private Optional<CompiledRule> findRule(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(request.getRequestURI());
        return rules.stream()
                .filter(compiled -> compiled.rule().method().equalsIgnoreCase(request.getMethod()))
                .filter(compiled -> compiled.patterns().stream().anyMatch(pattern -> pattern.matches(path)))
                .findFirst();
    }
}

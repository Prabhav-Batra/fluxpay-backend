package com.fluxpay.common.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Per-client-IP limits. In-process (ADR-004, TD-003): limits apply per backend instance. */
@Validated
@ConfigurationProperties("fluxpay.rate-limit")
public record RateLimitProperties(@Valid List<Rule> rules) {

    public RateLimitProperties {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public record Rule(
            @NotBlank String name,
            @NotBlank String method,
            @NotEmpty List<String> paths,
            @Min(1) int capacity,
            @NotNull Duration period) {}
}

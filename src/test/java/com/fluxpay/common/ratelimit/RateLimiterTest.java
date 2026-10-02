package com.fluxpay.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final RateLimitProperties.Rule rule =
            new RateLimitProperties.Rule("auth", "POST", List.of("/api/v1/auth/login"), 3, Duration.ofMinutes(1));
    private final RateLimiter limiter = new RateLimiter();

    @Test
    void should_allow_up_to_capacity_then_reject() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryConsume("1.2.3.4", rule).allowed()).isTrue();
        }

        RateLimitDecision rejected = limiter.tryConsume("1.2.3.4", rule);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.remaining()).isZero();
        assertThat(rejected.resetSeconds()).isPositive();
    }

    @Test
    void should_track_clients_independently() {
        for (int i = 0; i < 3; i++) {
            limiter.tryConsume("1.2.3.4", rule);
        }

        assertThat(limiter.tryConsume("5.6.7.8", rule).allowed()).isTrue();
    }

    @Test
    void should_report_remaining_tokens_when_allowed() {
        RateLimitDecision first = limiter.tryConsume("1.2.3.4", rule);

        assertThat(first.limit()).isEqualTo(3);
        assertThat(first.remaining()).isEqualTo(2);
    }
}

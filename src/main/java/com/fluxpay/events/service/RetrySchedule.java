package com.fluxpay.events.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Delay before the next attempt after {@code attempts} failed attempts; empty once all 8 attempts are used. */
public final class RetrySchedule {

    private static final List<Duration> DELAYS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(2),
            Duration.ofHours(6),
            Duration.ofHours(12),
            Duration.ofHours(24));

    private RetrySchedule() {}

    public static Optional<Duration> delayAfterAttempt(int attempts) {
        return attempts >= 1 && attempts <= DELAYS.size() ? Optional.of(DELAYS.get(attempts - 1)) : Optional.empty();
    }
}

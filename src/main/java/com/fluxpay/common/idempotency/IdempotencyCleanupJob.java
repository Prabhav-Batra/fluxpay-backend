package com.fluxpay.common.idempotency;

import com.fluxpay.common.config.JobsProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fluxpay.jobs", name = "enabled", havingValue = "true")
public class IdempotencyCleanupJob {

    private final IdempotencyService idempotencyService;
    private final JobsProperties properties;
    private final Clock clock;

    public IdempotencyCleanupJob(IdempotencyService idempotencyService, JobsProperties properties, Clock clock) {
        this.idempotencyService = idempotencyService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void run() {
        idempotencyService.deleteOlderThan(Instant.now(clock).minus(properties.idempotencyRetention()));
    }
}

package com.fluxpay.sales.jobs;

import com.fluxpay.sales.service.CheckoutExpiryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fluxpay.jobs", name = "enabled", havingValue = "true")
public class CheckoutExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(CheckoutExpiryJob.class);

    private final CheckoutExpiryService expiryService;

    public CheckoutExpiryJob(CheckoutExpiryService expiryService) {
        this.expiryService = expiryService;
    }

    @Scheduled(fixedDelayString = "${fluxpay.jobs.expiry-interval}")
    public void run() {
        int expired = expiryService.expireDue();
        if (expired > 0) {
            log.info("Expired {} checkout sessions", expired);
        }
    }
}

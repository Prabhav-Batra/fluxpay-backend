package com.fluxpay.events.jobs;

import com.fluxpay.events.service.WebhookDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fluxpay.jobs", name = "enabled", havingValue = "true")
public class WebhookDispatchJob {

    private final WebhookDispatcher dispatcher;

    public WebhookDispatchJob(WebhookDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${fluxpay.jobs.webhook-interval}")
    public void run() {
        while (dispatcher.dispatchDue() > 0) {
            // keep draining while full batches are due
        }
    }
}

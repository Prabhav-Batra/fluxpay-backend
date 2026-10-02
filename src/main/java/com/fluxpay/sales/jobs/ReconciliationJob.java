package com.fluxpay.sales.jobs;

import com.fluxpay.sales.service.ReconciliationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fluxpay.jobs", name = "enabled", havingValue = "true")
public class ReconciliationJob {

    private final ReconciliationService reconciliationService;

    public ReconciliationJob(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(
            fixedDelayString = "${fluxpay.jobs.reconciliation-interval}",
            initialDelayString = "${fluxpay.jobs.reconciliation-interval}")
    public void run() {
        reconciliationService.reconcile();
    }
}

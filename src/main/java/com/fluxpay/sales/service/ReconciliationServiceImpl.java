package com.fluxpay.sales.service;

import com.fluxpay.checkout.service.CheckoutService;
import com.fluxpay.checkout.service.CheckoutSessionView;
import com.fluxpay.common.config.JobsProperties;
import com.fluxpay.payments.service.GatewayPayment;
import com.fluxpay.payments.service.PaymentGateway;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Spec §6 step 5. Not transactional: gateway calls never run inside a transaction; capture opens its own. */
@Service
public class ReconciliationServiceImpl implements ReconciliationService {

    static final int BATCH_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(ReconciliationServiceImpl.class);

    private final CheckoutService checkoutService;
    private final PaymentGateway gateway;
    private final SaleCaptureService captureService;
    private final JobsProperties properties;
    private final Clock clock;

    public ReconciliationServiceImpl(
            CheckoutService checkoutService,
            PaymentGateway gateway,
            SaleCaptureService captureService,
            JobsProperties properties,
            Clock clock) {
        this.checkoutService = checkoutService;
        this.gateway = gateway;
        this.captureService = captureService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public int reconcile() {
        Instant now = Instant.now(clock);
        List<CheckoutSessionView> candidates = checkoutService.findReconcilable(
                now.minus(properties.reconciliationWindow()), now.minus(properties.reconciliationMinAge()), BATCH_SIZE);
        for (CheckoutSessionView session : candidates) {
            try {
                for (GatewayPayment payment : gateway.fetchOrderPayments(session.mode(), session.gatewayOrderId())) {
                    if (payment.isCaptured()) {
                        captureService.capture(session.mode(), payment);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Reconciliation failed for checkout session {}", session.id(), e);
            }
        }
        return candidates.size();
    }
}

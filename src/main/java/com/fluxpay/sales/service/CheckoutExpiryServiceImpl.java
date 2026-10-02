package com.fluxpay.sales.service;

import com.fluxpay.checkout.service.CheckoutService;
import com.fluxpay.checkout.service.CheckoutSessionView;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.events.domain.EventType;
import com.fluxpay.events.service.EventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutExpiryServiceImpl implements CheckoutExpiryService {

    static final int BATCH_SIZE = 100;

    private final CheckoutService checkoutService;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    public CheckoutExpiryServiceImpl(CheckoutService checkoutService, EventPublisher eventPublisher, Clock clock) {
        this.checkoutService = checkoutService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int expireDue() {
        List<CheckoutSessionView> expired = checkoutService.expireDue(Instant.now(clock), BATCH_SIZE);
        for (CheckoutSessionView session : expired) {
            eventPublisher.publish(
                    new TenantContext(session.merchantId(), session.mode()),
                    EventType.CHECKOUT_EXPIRED,
                    SalePayloads.expired(session));
        }
        return expired.size();
    }
}

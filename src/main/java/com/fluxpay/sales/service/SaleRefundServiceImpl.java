package com.fluxpay.sales.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.events.domain.EventType;
import com.fluxpay.events.service.EventPublisher;
import com.fluxpay.ledger.service.LedgerService;
import com.fluxpay.payments.domain.PaymentStatus;
import com.fluxpay.payments.service.GatewayRefund;
import com.fluxpay.payments.service.PaymentRecordService;
import com.fluxpay.payments.service.RecordedPayment;
import com.fluxpay.sales.domain.Sale;
import com.fluxpay.sales.persistence.SaleRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Spec §6 step 7. Refunds are initiated in the Razorpay dashboard; the platform fee is kept (TD-002). */
@Service
public class SaleRefundServiceImpl implements SaleRefundService {

    private static final Logger log = LoggerFactory.getLogger(SaleRefundServiceImpl.class);

    private final PaymentRecordService paymentRecords;
    private final SaleRepository sales;
    private final LedgerService ledgerService;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    public SaleRefundServiceImpl(
            PaymentRecordService paymentRecords,
            SaleRepository sales,
            LedgerService ledgerService,
            EventPublisher eventPublisher,
            Clock clock) {
        this.paymentRecords = paymentRecords;
        this.sales = sales;
        this.ledgerService = ledgerService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void refund(Mode mode, GatewayRefund refund) {
        Optional<RecordedPayment> payment = paymentRecords.findByGatewayPaymentId(refund.paymentId());
        if (payment.isEmpty()
                || payment.get().status() != PaymentStatus.CAPTURED
                || payment.get().mode() != mode) {
            log.warn("Refund {} for payment {} has no matching sale", refund.id(), refund.paymentId());
            return;
        }
        Optional<Sale> locked = sales.lockByPaymentId(payment.get().id());
        if (locked.isEmpty()) {
            log.warn("Refund {} for payment {} has no sale", refund.id(), refund.paymentId());
            return;
        }
        Sale sale = locked.get();
        TenantContext tenant = new TenantContext(sale.getMerchantId(), sale.getMode());
        boolean recorded =
                ledgerService.recordRefund(tenant, sale.getId(), refund.id(), refund.amount(), refund.currency());
        if (!recorded) {
            return;
        }
        sale.applyRefund(refund.amount(), Instant.now(clock));
        eventPublisher.publish(tenant, EventType.SALE_REFUNDED, SalePayloads.refunded(sale, refund.amount()));
    }
}

package com.fluxpay.sales.service;

import com.fluxpay.checkout.domain.CheckoutStatus;
import com.fluxpay.checkout.service.CheckoutService;
import com.fluxpay.checkout.service.CheckoutSessionView;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.events.domain.EventType;
import com.fluxpay.events.service.EventPublisher;
import com.fluxpay.ledger.service.LedgerService;
import com.fluxpay.payments.domain.PaymentStatus;
import com.fluxpay.payments.service.GatewayPayment;
import com.fluxpay.payments.service.NewPaymentRecord;
import com.fluxpay.payments.service.PaymentGateway;
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

/**
 * Spec §6 step 3. One transaction: payment row, session completion, sale, ledger entries and the
 * checkout.completed outbox event. The session row lock serialises concurrent deliveries of the same order.
 */
@Service
public class SaleCaptureServiceImpl implements SaleCaptureService {

    private static final Logger log = LoggerFactory.getLogger(SaleCaptureServiceImpl.class);

    private final CheckoutService checkoutService;
    private final PaymentRecordService paymentRecords;
    private final PaymentGateway gateway;
    private final SaleRepository sales;
    private final LedgerService ledgerService;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    public SaleCaptureServiceImpl(
            CheckoutService checkoutService,
            PaymentRecordService paymentRecords,
            PaymentGateway gateway,
            SaleRepository sales,
            LedgerService ledgerService,
            EventPublisher eventPublisher,
            Clock clock) {
        this.checkoutService = checkoutService;
        this.paymentRecords = paymentRecords;
        this.gateway = gateway;
        this.sales = sales;
        this.ledgerService = ledgerService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void capture(Mode mode, GatewayPayment payment) {
        if (!payment.isCaptured() || alreadyRecorded(payment)) {
            return;
        }
        Optional<CheckoutSessionView> locked = checkoutService.lockByGatewayOrderId(payment.orderId());
        if (locked.isEmpty()) {
            log.warn("Captured payment {} references unknown order {}", payment.id(), payment.orderId());
            return;
        }
        CheckoutSessionView session = locked.get();
        if (session.mode() != mode) {
            log.warn("Captured payment {} arrived on {} endpoint for a {} session", payment.id(), mode, session.mode());
            return;
        }
        if (alreadyRecorded(payment)) {
            return;
        }
        if (session.status() == CheckoutStatus.COMPLETED) {
            record(session, payment, PaymentStatus.DUPLICATE);
            log.error("Second payment {} for completed session {}: refund it manually", payment.id(), session.id());
            return;
        }
        if (payment.amount() != session.amount() || !session.currency().equals(payment.currency())) {
            record(session, payment, PaymentStatus.AMOUNT_MISMATCH);
            log.error(
                    "Payment {} amount {} does not match session {} amount {}",
                    payment.id(),
                    payment.amount(),
                    session.id(),
                    session.amount());
            return;
        }
        completeSale(session, payment);
    }

    private void completeSale(CheckoutSessionView session, GatewayPayment payment) {
        Instant now = Instant.now(clock);
        TenantContext tenant = new TenantContext(session.merchantId(), session.mode());
        RecordedPayment recorded = record(session, payment, PaymentStatus.CAPTURED);
        checkoutService.markCompleted(session.id(), now);
        Sale sale = sales.save(new Sale(
                session.merchantId(),
                session.mode(),
                session.productId(),
                session.id(),
                recorded.id(),
                session.customerRef(),
                session.amount(),
                session.currency(),
                session.metadata(),
                now));
        ledgerService.recordSale(tenant, sale.getId(), sale.getAmount(), sale.getCurrency(), payment.fee());
        eventPublisher.publish(tenant, EventType.CHECKOUT_COMPLETED, SalePayloads.completed(sale));
    }

    private boolean alreadyRecorded(GatewayPayment payment) {
        return paymentRecords.findByGatewayPaymentId(payment.id()).isPresent();
    }

    private RecordedPayment record(CheckoutSessionView session, GatewayPayment payment, PaymentStatus status) {
        return paymentRecords.record(new NewPaymentRecord(
                session.merchantId(), session.mode(), session.id(), gateway.name(), payment, status));
    }
}

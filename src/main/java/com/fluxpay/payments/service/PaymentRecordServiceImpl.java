package com.fluxpay.payments.service;

import com.fluxpay.payments.domain.Payment;
import com.fluxpay.payments.persistence.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentRecordServiceImpl implements PaymentRecordService {

    private final PaymentRepository payments;
    private final Clock clock;

    public PaymentRecordServiceImpl(PaymentRepository payments, Clock clock) {
        this.payments = payments;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecordedPayment> findByGatewayPaymentId(String gatewayPaymentId) {
        return payments.findByGatewayPaymentId(gatewayPaymentId).map(RecordedPayment::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecordedPayment> findById(UUID paymentId) {
        return payments.findById(paymentId).map(RecordedPayment::from);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RecordedPayment record(NewPaymentRecord record) {
        GatewayPayment payment = record.payment();
        Payment saved = payments.save(new Payment(
                record.merchantId(),
                record.mode(),
                record.checkoutSessionId(),
                record.gateway(),
                payment.id(),
                payment.orderId(),
                record.status(),
                payment.amount(),
                payment.currency(),
                payment.method(),
                payment.fee(),
                Instant.now(clock)));
        return RecordedPayment.from(saved);
    }
}

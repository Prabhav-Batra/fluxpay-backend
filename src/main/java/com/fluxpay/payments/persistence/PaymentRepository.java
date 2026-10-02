package com.fluxpay.payments.persistence;

import com.fluxpay.payments.domain.Payment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Lookups by gateway id serve the webhook flow; callers re-check tenant on the returned record. */
public interface PaymentRepository extends Repository<Payment, UUID> {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByGatewayPaymentId(String gatewayPaymentId);
}

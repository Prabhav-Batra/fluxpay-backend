package com.fluxpay.payments.service;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRecordService {

    Optional<RecordedPayment> findByGatewayPaymentId(String gatewayPaymentId);

    Optional<RecordedPayment> findById(UUID paymentId);

    /** Inserts in the caller's transaction. The unique gateway payment id makes duplicates fail loudly. */
    RecordedPayment record(NewPaymentRecord record);
}

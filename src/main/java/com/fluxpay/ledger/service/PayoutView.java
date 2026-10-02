package com.fluxpay.ledger.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.ledger.domain.Payout;
import java.time.Instant;
import java.util.UUID;

public record PayoutView(
        UUID id,
        Mode mode,
        long amount,
        String currency,
        String reference,
        Instant paidAt,
        UUID recordedBy,
        Instant createdAt) {

    static PayoutView from(Payout payout) {
        return new PayoutView(
                payout.getId(),
                payout.getMode(),
                payout.getAmount(),
                payout.getCurrency(),
                payout.getReference(),
                payout.getPaidAt(),
                payout.getRecordedBy(),
                payout.getCreatedAt());
    }
}

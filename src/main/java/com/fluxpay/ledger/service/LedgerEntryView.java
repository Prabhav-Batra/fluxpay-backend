package com.fluxpay.ledger.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.ledger.domain.LedgerEntry;
import com.fluxpay.ledger.domain.LedgerEntryType;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryView(
        UUID id,
        Mode mode,
        LedgerEntryType type,
        long amount,
        String currency,
        UUID saleId,
        String reference,
        Instant createdAt) {

    static LedgerEntryView from(LedgerEntry entry) {
        return new LedgerEntryView(
                entry.getId(),
                entry.getMode(),
                entry.getType(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getSaleId(),
                entry.getReference(),
                entry.getCreatedAt());
    }
}

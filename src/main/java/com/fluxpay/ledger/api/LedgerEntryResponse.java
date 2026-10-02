package com.fluxpay.ledger.api;

import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.ledger.domain.LedgerEntryType;
import com.fluxpay.ledger.service.LedgerEntryView;
import java.time.Instant;

public record LedgerEntryResponse(
        String id, Mode mode, LedgerEntryType type, long amount, String currency, String saleId, Instant createdAt) {

    public static LedgerEntryResponse from(LedgerEntryView view) {
        return new LedgerEntryResponse(
                PublicId.of(IdPrefix.LEDGER_ENTRY, view.id()),
                view.mode(),
                view.type(),
                view.amount(),
                view.currency(),
                view.saleId() == null ? null : PublicId.of(IdPrefix.SALE, view.saleId()),
                view.createdAt());
    }
}

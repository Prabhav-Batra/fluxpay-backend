package com.fluxpay.ledger.domain;

import com.fluxpay.common.id.UuidV7;
import com.fluxpay.common.tenant.Mode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only: entries are never updated or deleted. Credits are positive, debits negative. */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerEntryType type;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "sale_id")
    private UUID saleId;

    private String reference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {}

    public LedgerEntry(
            UUID merchantId,
            Mode mode,
            LedgerEntryType type,
            long amount,
            String currency,
            UUID saleId,
            String reference,
            Instant now) {
        this.id = UuidV7.generate();
        this.merchantId = merchantId;
        this.mode = mode;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.saleId = saleId;
        this.reference = reference;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Mode getMode() {
        return mode;
    }

    public LedgerEntryType getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public UUID getSaleId() {
        return saleId;
    }

    public String getReference() {
        return reference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

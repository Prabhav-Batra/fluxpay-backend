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

/** Money the platform sent to a merchant (ADR-002 phase 1: recorded manually by the platform admin). */
@Entity
@Table(name = "payouts")
public class Payout {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String reference;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Payout() {}

    public Payout(
            UUID merchantId,
            Mode mode,
            long amount,
            String currency,
            String reference,
            Instant paidAt,
            UUID recordedBy,
            Instant now) {
        this.id = UuidV7.generate();
        this.merchantId = merchantId;
        this.mode = mode;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
        this.paidAt = paidAt;
        this.recordedBy = recordedBy;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Mode getMode() {
        return mode;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReference() {
        return reference;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

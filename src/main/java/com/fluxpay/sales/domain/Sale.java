package com.fluxpay.sales.domain;

import com.fluxpay.common.id.UuidV7;
import com.fluxpay.common.tenant.Mode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** FluxPay's record that a customer bought a product (spec §5). Exactly one per captured payment. */
@Entity
@Table(name = "sales")
public class Sale {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "checkout_session_id", nullable = false)
    private UUID checkoutSessionId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "customer_ref")
    private String customerRef;

    @Column(nullable = false)
    private long amount;

    @Column(name = "refunded_amount", nullable = false)
    private long refundedAmount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SaleStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Sale() {}

    public Sale(
            UUID merchantId,
            Mode mode,
            UUID productId,
            UUID checkoutSessionId,
            UUID paymentId,
            String customerRef,
            long amount,
            String currency,
            Map<String, String> metadata,
            Instant now) {
        this.id = UuidV7.generate();
        this.merchantId = merchantId;
        this.mode = mode;
        this.productId = productId;
        this.checkoutSessionId = checkoutSessionId;
        this.paymentId = paymentId;
        this.customerRef = customerRef;
        this.amount = amount;
        this.currency = currency;
        this.metadata = metadata;
        this.status = SaleStatus.PAID;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void applyRefund(long refund, Instant now) {
        this.refundedAmount += refund;
        this.status = refundedAmount >= amount ? SaleStatus.REFUNDED : SaleStatus.PARTIALLY_REFUNDED;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public Mode getMode() {
        return mode;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getCheckoutSessionId() {
        return checkoutSessionId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getCustomerRef() {
        return customerRef;
    }

    public long getAmount() {
        return amount;
    }

    public long getRefundedAmount() {
        return refundedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

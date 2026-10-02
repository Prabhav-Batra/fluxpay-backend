package com.fluxpay.checkout.domain;

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

@Entity
@Table(name = "checkout_sessions")
public class CheckoutSession {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "payment_link_id")
    private UUID paymentLinkId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "customer_ref")
    private String customerRef;

    @Column(name = "success_url")
    private String successUrl;

    @Column(name = "cancel_url")
    private String cancelUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> metadata;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckoutStatus status;

    @Column(name = "gateway_order_id")
    private String gatewayOrderId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CheckoutSession() {}

    public CheckoutSession(
            UUID merchantId,
            Mode mode,
            UUID productId,
            UUID paymentLinkId,
            long amount,
            String currency,
            String customerRef,
            String successUrl,
            String cancelUrl,
            Map<String, String> metadata,
            Instant expiresAt,
            Instant now) {
        this.id = UuidV7.generate();
        this.merchantId = merchantId;
        this.mode = mode;
        this.productId = productId;
        this.paymentLinkId = paymentLinkId;
        this.amount = amount;
        this.currency = currency;
        this.customerRef = customerRef;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
        this.metadata = metadata;
        this.status = CheckoutStatus.OPEN;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    /** Allowed from OPEN or EXPIRED: a payment captured after expiry still took the customer's money. */
    public void complete(Instant now) {
        this.status = CheckoutStatus.COMPLETED;
        this.completedAt = now;
    }

    public void expire() {
        if (status == CheckoutStatus.OPEN) {
            this.status = CheckoutStatus.EXPIRED;
        }
    }

    public boolean isPayableAt(Instant now) {
        return status == CheckoutStatus.OPEN && now.isBefore(expiresAt);
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

    public UUID getPaymentLinkId() {
        return paymentLinkId;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCustomerRef() {
        return customerRef;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public CheckoutStatus getStatus() {
        return status;
    }

    public String getGatewayOrderId() {
        return gatewayOrderId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

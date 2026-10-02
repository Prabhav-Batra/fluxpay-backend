package com.fluxpay.payments.domain;

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

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @Column(name = "checkout_session_id", nullable = false)
    private UUID checkoutSessionId;

    @Column(nullable = false)
    private String gateway;

    @Column(name = "gateway_payment_id", nullable = false)
    private String gatewayPaymentId;

    @Column(name = "gateway_order_id", nullable = false)
    private String gatewayOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String currency;

    private String method;

    @Column(name = "gateway_fee", nullable = false)
    private long gatewayFee;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Payment() {}

    public Payment(
            UUID merchantId,
            Mode mode,
            UUID checkoutSessionId,
            String gateway,
            String gatewayPaymentId,
            String gatewayOrderId,
            PaymentStatus status,
            long amount,
            String currency,
            String method,
            long gatewayFee,
            Instant now) {
        this.id = UuidV7.generate();
        this.merchantId = merchantId;
        this.mode = mode;
        this.checkoutSessionId = checkoutSessionId;
        this.gateway = gateway;
        this.gatewayPaymentId = gatewayPaymentId;
        this.gatewayOrderId = gatewayOrderId;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.method = method;
        this.gatewayFee = gatewayFee;
        this.createdAt = now;
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

    public UUID getCheckoutSessionId() {
        return checkoutSessionId;
    }

    public String getGatewayPaymentId() {
        return gatewayPaymentId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getMethod() {
        return method;
    }

    public long getGatewayFee() {
        return gatewayFee;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

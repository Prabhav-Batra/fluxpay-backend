package com.fluxpay.events.domain;

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

/** One event sent to one endpoint, with its attempts and retry schedule (spec §7). */
@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    static final int MAX_ERROR_LENGTH = 500;

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "lease_token")
    private UUID leaseToken;

    @Column(name = "last_status_code")
    private Integer lastStatusCode;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebhookDelivery() {}

    public WebhookDelivery(UUID eventId, UUID endpointId, UUID merchantId, Mode mode, Instant now) {
        this.id = UuidV7.generate();
        this.eventId = eventId;
        this.endpointId = endpointId;
        this.merchantId = merchantId;
        this.mode = mode;
        this.status = DeliveryStatus.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Claims the delivery until {@code until}; only the holder of {@code token} may record the outcome. */
    public void lease(Instant until, UUID token) {
        this.nextAttemptAt = until;
        this.leaseToken = token;
    }

    public boolean isLeasedBy(UUID token) {
        return status == DeliveryStatus.PENDING && token.equals(leaseToken);
    }

    public void succeeded(int statusCode, Instant now) {
        record(statusCode, null, now);
        this.status = DeliveryStatus.SUCCEEDED;
        this.nextAttemptAt = null;
    }

    /** {@code retryAt} null means no attempts remain. */
    public void failed(Integer statusCode, String error, Instant retryAt, Instant now) {
        record(statusCode, error, now);
        this.status = retryAt == null ? DeliveryStatus.FAILED : DeliveryStatus.PENDING;
        this.nextAttemptAt = retryAt;
    }

    private void record(Integer statusCode, String error, Instant now) {
        this.attemptCount++;
        this.lastStatusCode = statusCode;
        this.lastError =
                error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
        this.lastAttemptAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public Mode getMode() {
        return mode;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Integer getLastStatusCode() {
        return lastStatusCode;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

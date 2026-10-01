package com.fluxpay.webhook.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Same for every endpoint receiving this event; sent as X-Fluxpay-Event-Id so merchants can dedupe
    @Column(nullable = false, name = "event_id")
    private UUID eventId;

    @Column(nullable = false, name = "merchant_id")
    private UUID merchantId;

    // Null when sent to the FLUXPAY_DIRECT_WEBHOOK_URL override
    @Column(name = "endpoint_id")
    private UUID endpointId;

    @Column(nullable = false, name = "event_type")
    private String eventType;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WebhookDeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_response_status")
    private Integer lastResponseStatus;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}

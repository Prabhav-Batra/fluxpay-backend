package com.fluxpay.events.service;

import com.fluxpay.events.domain.DeliveryStatus;
import com.fluxpay.events.domain.WebhookDelivery;
import java.time.Instant;
import java.util.UUID;

public record DeliveryView(
        UUID id,
        UUID endpointId,
        DeliveryStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        Integer lastStatusCode,
        String lastError,
        Instant lastAttemptAt,
        Instant createdAt) {

    static DeliveryView from(WebhookDelivery delivery) {
        return new DeliveryView(
                delivery.getId(),
                delivery.getEndpointId(),
                delivery.getStatus(),
                delivery.getAttemptCount(),
                delivery.getNextAttemptAt(),
                delivery.getLastStatusCode(),
                delivery.getLastError(),
                delivery.getLastAttemptAt(),
                delivery.getCreatedAt());
    }
}

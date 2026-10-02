package com.fluxpay.events.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.events.domain.DeliveryStatus;
import com.fluxpay.events.service.DeliveryView;
import com.fluxpay.events.service.EventDetailView;
import java.time.Instant;
import java.util.List;

public record EventDetailResponse(@JsonUnwrapped EventResponse event, List<Delivery> deliveries) {

    public record Delivery(
            String id,
            String endpointId,
            DeliveryStatus status,
            int attemptCount,
            Instant nextAttemptAt,
            Integer lastStatusCode,
            String lastError,
            Instant lastAttemptAt,
            Instant createdAt) {

        static Delivery from(DeliveryView view) {
            return new Delivery(
                    PublicId.of(IdPrefix.WEBHOOK_DELIVERY, view.id()),
                    PublicId.of(IdPrefix.WEBHOOK_ENDPOINT, view.endpointId()),
                    view.status(),
                    view.attemptCount(),
                    view.nextAttemptAt(),
                    view.lastStatusCode(),
                    view.lastError(),
                    view.lastAttemptAt(),
                    view.createdAt());
        }
    }

    public static EventDetailResponse from(EventDetailView view) {
        return new EventDetailResponse(
                EventResponse.from(view.event()),
                view.deliveries().stream().map(Delivery::from).toList());
    }
}

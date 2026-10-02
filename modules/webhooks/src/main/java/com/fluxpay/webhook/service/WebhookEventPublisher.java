package com.fluxpay.webhook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxpay.webhook.entity.WebhookDelivery;
import com.fluxpay.webhook.entity.WebhookDeliveryStatus;
import com.fluxpay.webhook.entity.WebhookEndpoint;
import com.fluxpay.webhook.repository.WebhookDeliveryRepository;
import com.fluxpay.webhook.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Records outgoing merchant webhooks in webhook_deliveries (transactional outbox) and hands them
 * to {@link WebhookDispatcher}, which sends them after the caller's transaction commits.
 * Because the rows are written in the caller's transaction, an event exists if and only if the
 * state change that caused it (e.g. order PAID) was committed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookEventPublisher {

    private final ObjectMapper objectMapper;
    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final WebhookDispatcher webhookDispatcher;

    @Transactional
    public void publishEvent(UUID merchantId, String eventType, Object payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Could not serialize webhook event {} for merchant {}", eventType, merchantId, e);
            return;
        }

        UUID eventId = UUID.randomUUID();
        List<WebhookDelivery> deliveries = new ArrayList<>();

        // Each merchant registers its own endpoints in the dashboard; each endpoint has its own secret
        for (WebhookEndpoint endpoint : webhookEndpointRepository.findByMerchantIdAndActiveTrue(merchantId)) {
            deliveries.add(newDelivery(eventId, merchantId, endpoint.getId(), endpoint.getUrl(), eventType, payloadJson));
        }

        if (deliveries.isEmpty()) {
            log.warn("Webhook event {} for merchant {} not delivered: no active webhook endpoint registered", eventType, merchantId);
            return;
        }

        List<UUID> ids = webhookDeliveryRepository.saveAll(deliveries).stream().map(WebhookDelivery::getId).toList();
        log.info("Queued webhook event {} ({}) for merchant {} to {} endpoint(s)", eventType, eventId, merchantId, ids.size());
        webhookDispatcher.dispatchAfterCommit(ids);
    }

    private static WebhookDelivery newDelivery(UUID eventId, UUID merchantId, UUID endpointId, String url,
                                               String eventType, String payloadJson) {
        return WebhookDelivery.builder()
                .eventId(eventId)
                .merchantId(merchantId)
                .endpointId(endpointId)
                .url(url)
                .eventType(eventType)
                .payload(payloadJson)
                .status(WebhookDeliveryStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(Instant.now())
                .build();
    }
}

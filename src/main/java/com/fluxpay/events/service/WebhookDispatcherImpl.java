package com.fluxpay.events.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.events.domain.Event;
import com.fluxpay.events.domain.WebhookDelivery;
import com.fluxpay.events.domain.WebhookEndpoint;
import com.fluxpay.events.persistence.EventRepository;
import com.fluxpay.events.persistence.WebhookDeliveryRepository;
import com.fluxpay.events.persistence.WebhookEndpointRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Spec §7 delivery. Claiming leases rows (pushes next_attempt_at forward) in a short transaction so concurrent
 * dispatchers never send the same delivery; HTTP runs with no transaction open; each result commits on its own.
 */
@Service
public class WebhookDispatcherImpl implements WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcherImpl.class);

    private record Claim(UUID deliveryId, UUID eventId, UUID endpointId, int attempts) {}

    private record Outcome(Integer statusCode, String error) {
        boolean succeeded() {
            return statusCode != null && statusCode >= 200 && statusCode < 300;
        }
    }

    private final WebhookDeliveryRepository deliveries;
    private final WebhookEndpointRepository endpoints;
    private final EventRepository events;
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final WebhookProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public WebhookDispatcherImpl(
            WebhookDeliveryRepository deliveries,
            WebhookEndpointRepository endpoints,
            EventRepository events,
            @Qualifier("webhookRestClient") RestClient client,
            ObjectMapper objectMapper,
            WebhookProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.deliveries = deliveries;
        this.endpoints = endpoints;
        this.events = events;
        this.client = client;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public int dispatchDue() {
        List<Claim> claims = transaction.execute(status -> claim(Instant.now(clock)));
        claims.forEach(this::attempt);
        return claims.size();
    }

    private List<Claim> claim(Instant now) {
        return deliveries.lockDue(now, properties.batchSize()).stream()
                .map(delivery -> {
                    delivery.lease(now.plus(properties.lease()));
                    return new Claim(
                            delivery.getId(),
                            delivery.getEventId(),
                            delivery.getEndpointId(),
                            delivery.getAttemptCount());
                })
                .toList();
    }

    private void attempt(Claim claim) {
        Optional<WebhookEndpoint> endpoint = endpoints.findById(claim.endpointId());
        Optional<Event> event = events.findById(claim.eventId());
        Outcome outcome;
        if (endpoint.isEmpty() || event.isEmpty() || !endpoint.get().isDeliverable()) {
            outcome = new Outcome(null, "Endpoint disabled or deleted");
            record(claim, outcome, true);
            return;
        }
        if (!WebhookUrlPolicy.isAllowed(endpoint.get().getMode(), endpoint.get().getUrl())) {
            record(claim, new Outcome(null, "Endpoint URL is not allowed"), true);
            return;
        }
        record(claim, send(endpoint.get(), event.get()), false);
    }

    private Outcome send(WebhookEndpoint endpoint, Event event) {
        byte[] body = body(event);
        String signature = WebhookSigner.header(
                endpoint.getSecret(), body, Instant.now(clock).getEpochSecond());
        try {
            int status = client.post()
                    .uri(endpoint.getUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("FluxPay-Signature", signature)
                    .header("FluxPay-Event-Id", PublicId.of(IdPrefix.EVENT, event.getId()))
                    .header("FluxPay-Event-Type", event.getType().value())
                    .header("User-Agent", "FluxPay-Webhooks/1.0")
                    .body(body)
                    .exchange((request, response) -> response.getStatusCode().value());
            return new Outcome(status, status >= 300 ? "HTTP " + status : null);
        } catch (RestClientException e) {
            return new Outcome(null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void record(Claim claim, Outcome outcome, boolean terminal) {
        transaction.executeWithoutResult(status -> {
            Instant now = Instant.now(clock);
            WebhookDelivery delivery = deliveries.findById(claim.deliveryId()).orElseThrow();
            if (outcome.succeeded()) {
                delivery.succeeded(outcome.statusCode(), now);
                return;
            }
            Instant retryAt = terminal
                    ? null
                    : RetrySchedule.delayAfterAttempt(claim.attempts() + 1)
                            .map(now::plus)
                            .orElse(null);
            delivery.failed(outcome.statusCode(), outcome.error(), retryAt, now);
            if (retryAt == null) {
                log.warn("Webhook delivery {} failed permanently: {}", claim.deliveryId(), outcome.error());
            }
        });
    }

    private byte[] body(Event event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", PublicId.of(IdPrefix.EVENT, event.getId()));
        payload.put("type", event.getType().value());
        payload.put("created", event.getCreatedAt().getEpochSecond());
        payload.put("mode", event.getMode().value());
        payload.put("data", event.getData());
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise webhook payload", e);
        }
    }
}

package com.fluxpay.webhook.service;

import com.fluxpay.webhook.entity.WebhookDelivery;
import com.fluxpay.webhook.entity.WebhookDeliveryStatus;
import com.fluxpay.webhook.entity.WebhookEndpoint;
import com.fluxpay.webhook.repository.WebhookDeliveryRepository;
import com.fluxpay.webhook.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Delivers rows from webhook_deliveries inside the API process (replaces the Redis queue and
 * the separate fluxpay-webhook-worker service).
 *
 * No polling: each delivery is attempted right after the publishing transaction commits, retries
 * are scheduled in memory, and on startup any still-PENDING rows are rescheduled - so a restart or
 * a Render sleep/wake loses nothing. The database is only touched when there is work to do.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookDispatcher {

    // Wait before retry N (after attempt N failed). Attempts = BACKOFF.length + 1, spanning ~3 hours.
    private static final Duration[] BACKOFF = {
            Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10),
            Duration.ofMinutes(30), Duration.ofHours(2)
    };
    private static final int MAX_ATTEMPTS = BACKOFF.length + 1;
    private static final Duration RETENTION = Duration.ofDays(30);

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final TaskScheduler taskScheduler;
    private final RestTemplate restTemplate;

    @Value("${fluxpay.webhook.direct.secret:}")
    private String directSecret;

    /** Attempt these deliveries once the current transaction commits (immediately if there is none). */
    public void dispatchAfterCommit(List<UUID> deliveryIds) {
        Runnable dispatch = () -> deliveryIds.forEach(id -> schedule(id, Instant.now()));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumePendingDeliveries() {
        List<WebhookDelivery> pending = deliveryRepository.findByStatus(WebhookDeliveryStatus.PENDING);
        if (!pending.isEmpty()) {
            log.info("Resuming {} pending webhook deliveries", pending.size());
        }
        Instant now = Instant.now();
        for (WebhookDelivery delivery : pending) {
            Instant due = delivery.getNextAttemptAt();
            schedule(delivery.getId(), due == null || due.isBefore(now) ? now : due);
        }
    }

    // Once a day, drop successful deliveries older than the retention window. FAILED rows are kept.
    @Scheduled(cron = "0 30 3 * * *")
    public void purgeOldDeliveries() {
        int removed = deliveryRepository.deleteOld(WebhookDeliveryStatus.DELIVERED, Instant.now().minus(RETENTION));
        if (removed > 0) {
            log.info("Purged {} delivered webhooks older than {} days", removed, RETENTION.toDays());
        }
    }

    private void schedule(UUID deliveryId, Instant when) {
        taskScheduler.schedule(() -> attempt(deliveryId), when);
    }

    void attempt(UUID deliveryId) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId).orElse(null);
        if (delivery == null || delivery.getStatus() != WebhookDeliveryStatus.PENDING) {
            return;
        }

        String secret = resolveSecret(delivery);
        if (secret == null) {
            giveUp(delivery, null, "No signing secret: endpoint deactivated or FLUXPAY_DIRECT_WEBHOOK_SECRET unset");
            deliveryRepository.save(delivery);
            return;
        }

        delivery.setAttempts(delivery.getAttempts() + 1);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Fluxpay-Signature", sign(delivery.getPayload(), secret));
            headers.set("X-Fluxpay-Event", delivery.getEventType());
            headers.set("X-Fluxpay-Event-Id", delivery.getEventId().toString());
            headers.set("X-Fluxpay-Delivery-Attempt", String.valueOf(delivery.getAttempts()));

            var response = restTemplate.postForEntity(delivery.getUrl(), new HttpEntity<>(delivery.getPayload(), headers), String.class);

            delivery.setStatus(WebhookDeliveryStatus.DELIVERED);
            delivery.setDeliveredAt(Instant.now());
            delivery.setNextAttemptAt(null);
            delivery.setLastResponseStatus(response.getStatusCode().value());
            delivery.setLastError(null);
            log.info("✅ Webhook {} ({}) delivered to {} on attempt {}",
                    delivery.getEventType(), delivery.getEventId(), delivery.getUrl(), delivery.getAttempts());
        } catch (HttpStatusCodeException e) {
            retryOrGiveUp(delivery, e.getStatusCode().value(), "HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            retryOrGiveUp(delivery, null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        deliveryRepository.save(delivery);
        if (delivery.getStatus() == WebhookDeliveryStatus.PENDING) {
            schedule(delivery.getId(), delivery.getNextAttemptAt());
        }
    }

    private void retryOrGiveUp(WebhookDelivery delivery, Integer responseStatus, String error) {
        if (delivery.getAttempts() >= MAX_ATTEMPTS) {
            giveUp(delivery, responseStatus, error);
            return;
        }
        Duration wait = BACKOFF[delivery.getAttempts() - 1];
        delivery.setLastResponseStatus(responseStatus);
        delivery.setLastError(truncate(error));
        delivery.setNextAttemptAt(Instant.now().plus(wait));
        log.warn("🔁 Webhook {} to {} failed (attempt {}/{}: {}), retrying in {}",
                delivery.getEventType(), delivery.getUrl(), delivery.getAttempts(), MAX_ATTEMPTS, error, wait);
    }

    private void giveUp(WebhookDelivery delivery, Integer responseStatus, String error) {
        delivery.setStatus(WebhookDeliveryStatus.FAILED);
        delivery.setNextAttemptAt(null);
        delivery.setLastResponseStatus(responseStatus);
        delivery.setLastError(truncate(error));
        log.error("🚨 Webhook {} ({}) to {} FAILED permanently after {} attempts: {}",
                delivery.getEventType(), delivery.getEventId(), delivery.getUrl(), delivery.getAttempts(), error);
    }

    private String resolveSecret(WebhookDelivery delivery) {
        if (delivery.getEndpointId() == null) {
            return directSecret.isBlank() ? null : directSecret;
        }
        return endpointRepository.findById(delivery.getEndpointId())
                .filter(WebhookEndpoint::isActive)
                .map(WebhookEndpoint::getSecretKey)
                .orElse(null);
    }

    private static String truncate(String value) {
        return value != null && value.length() > 500 ? value.substring(0, 500) : value;
    }

    static String sign(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}

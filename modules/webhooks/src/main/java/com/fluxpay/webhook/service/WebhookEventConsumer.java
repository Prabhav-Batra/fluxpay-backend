package com.fluxpay.webhook.service;

import com.fluxpay.webhook.repository.WebhookEndpointRepository;
import com.fluxpay.webhook.entity.WebhookEndpoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fluxpay.webhook.worker.enabled", havingValue = "true")
public class WebhookEventConsumer {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final WebhookEndpointRepository webhookEndpointRepository;
    
    private static final String WEBHOOK_QUEUE = "fluxpay:webhooks:queue";
    private static final String RETRY_QUEUE = "fluxpay:webhooks:retry";
    private static final String DLQ = "fluxpay:webhooks:dlq";
    private static final int MAX_RETRIES = 3;

    @Scheduled(fixedDelay = 2000)
    public void consumeWebhooks() {
        Object eventObj = redisTemplate.opsForList().leftPop(WEBHOOK_QUEUE);
        
        while (eventObj != null) {
            processEvent(eventObj);
            eventObj = redisTemplate.opsForList().leftPop(WEBHOOK_QUEUE);
        }
    }

    @Scheduled(fixedDelay = 10000)
    public void consumeRetries() {
        Object eventObj = redisTemplate.opsForList().leftPop(RETRY_QUEUE);
        
        while (eventObj != null) {
            log.info("Processing retry event");
            processEvent(eventObj);
            eventObj = redisTemplate.opsForList().leftPop(RETRY_QUEUE);
        }
    }

    private void processEvent(Object eventObj) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = (Map<String, Object>) eventObj;
            
            String merchantIdStr = (String) event.get("merchantId");
            if (merchantIdStr == null) {
                log.warn("Webhook event missing merchantId, dropping: {}", event);
                return;
            }
            
            UUID merchantId = UUID.fromString(merchantIdStr);
            List<WebhookEndpoint> endpoints = webhookEndpointRepository.findByMerchantIdAndActiveTrue(merchantId);
            
            if (endpoints.isEmpty()) {
                log.info("No active webhook endpoints for merchant {}", merchantId);
                return;
            }
            
            String payloadJson = objectMapper.writeValueAsString(event.get("payload"));
            
            for (WebhookEndpoint endpoint : endpoints) {
                sendToEndpoint(endpoint, payloadJson, event);
            }
            
        } catch (Exception e) {
            log.error("Critical error processing webhook event", e);
        }
    }
    
    private void sendToEndpoint(WebhookEndpoint endpoint, String payloadJson, Map<String, Object> originalEvent) {
        try {
            String signature = generateHmacSignature(payloadJson, endpoint.getSecretKey());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Fluxpay-Signature", signature);
            headers.set("X-Fluxpay-Event", (String) originalEvent.get("eventType"));
            
            HttpEntity<String> request = new HttpEntity<>(payloadJson, headers);
            
            log.info("Dispatching webhook to {}", endpoint.getUrl());
            restTemplate.postForEntity(endpoint.getUrl(), request, String.class);
            log.info("Successfully dispatched webhook to {}", endpoint.getUrl());
            
        } catch (Exception e) {
            log.error("Failed to send webhook to {}. Error: {}", endpoint.getUrl(), e.getMessage());
            handleRetry(originalEvent, endpoint.getUrl());
        }
    }
    
    private void handleRetry(Map<String, Object> event, String url) {
        int attempt = (Integer) event.getOrDefault("retryCount", 0);
        attempt++;
        
        event.put("retryCount", attempt);
        event.put("failedUrl", url);
        
        if (attempt <= MAX_RETRIES) {
            log.info("Scheduling webhook for retry {}/{}", attempt, MAX_RETRIES);
            redisTemplate.opsForList().rightPush(RETRY_QUEUE, event);
        } else {
            log.error("Webhook max retries exceeded. Moving to DLQ.");
            redisTemplate.opsForList().rightPush(DLQ, event);
        }
    }

    private String generateHmacSignature(String payload, String secret) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        return Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}

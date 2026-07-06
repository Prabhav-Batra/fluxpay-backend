package com.fluxpay.webhook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookEventPublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String WEBHOOK_QUEUE = "fluxpay:webhooks:queue";

    public void publishEvent(java.util.UUID merchantId, String eventType, Object payload) {
        log.info("Publishing webhook event to queue: {} for merchant {}", eventType, merchantId);
        
        Map<String, Object> event = Map.of(
            "merchantId", merchantId.toString(),
            "eventType", eventType,
            "payload", payload,
            "timestamp", System.currentTimeMillis()
        );
        
        try {
            redisTemplate.opsForList().rightPush(WEBHOOK_QUEUE, event);
        } catch (Exception e) {
            log.error("Failed to publish webhook event to Redis (is Redis running?). Error: {}", e.getMessage());
            // Fallback: Just log it in development mode so the payment doesn't fail
            log.warn("Fallback: Webhook Event Data: {}", event);
        }
    }
}

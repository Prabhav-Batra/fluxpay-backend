package com.fluxpay.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookEventPublisher {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public void publishEvent(java.util.UUID merchantId, String eventType, Object payload) {
        log.info("⚡ BYPASSING REDIS: Directly dispatching webhook event {} for merchant {}", eventType, merchantId);
        
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            
            // Hardcoding the Jextter endpoint directly to bypass database lookups entirely!
            String targetUrl = "https://despise-elude-gatherer.ngrok-free.dev/api/fluxpay/webhook";
            String secret = "whsec_9212c402f9e146e4b518d23c80b6829f";

            String signature = generateHmacSignature(payloadJson, secret);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Fluxpay-Signature", signature);
            headers.set("X-Fluxpay-Event", eventType);
            
            HttpEntity<String> request = new HttpEntity<>(payloadJson, headers);
            
            log.info("🚀 Sending DIRECT webhook to {}", targetUrl);
            restTemplate.postForEntity(targetUrl, request, String.class);
            log.info("✅ Successfully dispatched DIRECT webhook to Jextter!");
            
        } catch (Exception e) {
            log.error("🚨 Failed to send DIRECT webhook to Jextter. Error: {}", e.getMessage(), e);
        }
    }

    private String generateHmacSignature(String payload, String secret) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        return Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}

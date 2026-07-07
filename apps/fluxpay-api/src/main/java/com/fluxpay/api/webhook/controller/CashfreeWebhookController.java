package com.fluxpay.api.webhook.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxpay.order.service.OrderFulfillmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks/cashfree")
@RequiredArgsConstructor
@Slf4j
public class CashfreeWebhookController {

    private final ObjectMapper objectMapper;
    private final OrderFulfillmentService orderFulfillmentService;

    @Value("${fluxpay.gateways.cashfree.secret-key}")
    private String secretKey;

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestHeader("x-webhook-signature") String signature,
            @RequestHeader("x-webhook-timestamp") String timestamp,
            @RequestBody String rawBody) {

        log.info("Received Cashfree webhook at timestamp: {}", timestamp);

        try {
            // Verify Signature
            String data = timestamp + rawBody;
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            String expectedSignature = Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8)));

            if (!expectedSignature.equals(signature)) {
                log.warn("Invalid Cashfree webhook signature");
                return ResponseEntity.status(401).body("Invalid signature");
            }

            JsonNode payload = objectMapper.readTree(rawBody);
            String event = payload.get("type").asText();
            
            if ("PAYMENT_SUCCESS_WEBHOOK".equals(event)) {
                JsonNode dataNode = payload.get("data");
                JsonNode orderNode = dataNode.get("order");
                
                String orderIdStr = orderNode.get("order_id").asText();
                UUID orderId = UUID.fromString(orderIdStr);
                
                log.info("Processing successful payment for order: {}", orderId);
                
                // Call fulfillment service
                orderFulfillmentService.fulfillOrder(orderId);
            }

            return ResponseEntity.ok("Webhook processed");

        } catch (Exception e) {
            log.error("Error processing Cashfree webhook", e);
            return ResponseEntity.status(500).body("Internal error");
        }
    }
}

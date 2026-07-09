package com.fluxpay.payment.service;

import com.fluxpay.order.dto.OrderDto;
import com.fluxpay.order.service.OrderService;
import com.fluxpay.payment.entity.PaymentIntent;
import com.fluxpay.payment.entity.PaymentIntentStatus;
import com.fluxpay.payment.repository.PaymentIntentRepository;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import com.fluxpay.webhook.service.WebhookEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayWebhookService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final OrderService orderService;
    private final WebhookEventPublisher webhookEventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processCashfreeWebhook(String rawPayload) {
        log.info("Received Cashfree webhook: {}", rawPayload);

        try {
            Map<String, Object> payload = objectMapper.readValue(rawPayload, new TypeReference<Map<String, Object>>() {});
            // In a real integration, we parse Cashfree's specific nested JSON structure:
            // e.g., payload.get("data").get("order").get("order_id")
            // For now, we assume a simplified structure or extract it robustly:
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            if (data == null) {
                log.warn("Cashfree webhook missing 'data' object. Ignored.");
                return;
            }

            Map<String, Object> orderData = (Map<String, Object>) data.get("order");
            if (orderData == null) {
                log.warn("Cashfree webhook missing 'data.order' object. Ignored.");
                return;
            }

            String orderIdStr = (String) orderData.get("order_id");
            if (orderIdStr == null) {
                log.warn("Cashfree webhook missing 'data.order.order_id'. Ignored.");
                return;
            }

            UUID orderId = UUID.fromString(orderIdStr);

            // Find the PaymentIntent for this Order
            List<PaymentIntent> intents = paymentIntentRepository.findByOrderId(orderId);
            if (intents.isEmpty()) {
                throw new ResourceNotFoundException("PaymentIntent for Order", orderIdStr);
            }

            // In a real system, you'd match the specific intent by gatewayReference.
            // Assuming the latest intent is the active one.
            PaymentIntent intent = intents.get(intents.size() - 1);

            // Check if payment was successful
            Map<String, Object> paymentData = (Map<String, Object>) data.get("payment");
            String paymentStatus = paymentData != null ? (String) paymentData.get("payment_status") : null;

            if ("SUCCESS".equalsIgnoreCase(paymentStatus)) {
                intent.setStatus(PaymentIntentStatus.CAPTURED);
                paymentIntentRepository.save(intent);
                log.info("PaymentIntent {} marked as CAPTURED", intent.getId());

                // Find Merchant ID to dispatch downstream webhook
                OrderDto order = orderService.getOrder(orderId);
                
                // Dispatch Webhook to Merchant!
                Map<String, Object> downstreamPayload = Map.of(
                    "payment_intent_id", intent.getId().toString(),
                    "order_id", order.getId().toString(),
                    "order_reference", order.getOrderReference() != null ? order.getOrderReference() : "",
                    "amount", intent.getAmount(),
                    "currency", intent.getCurrency(),
                    "status", "SUCCESS"
                );

                webhookEventPublisher.publishEvent(order.getMerchantId(), "payment.succeeded", downstreamPayload);
                log.info("Dispatched downstream webhook payment.succeeded for Merchant {}", order.getMerchantId());

            } else if ("FAILED".equalsIgnoreCase(paymentStatus)) {
                intent.setStatus(PaymentIntentStatus.FAILED);
                paymentIntentRepository.save(intent);
                log.info("PaymentIntent {} marked as FAILED", intent.getId());
                
                OrderDto order = orderService.getOrder(orderId);
                webhookEventPublisher.publishEvent(order.getMerchantId(), "payment.failed", Map.of(
                    "payment_intent_id", intent.getId().toString(),
                    "order_id", order.getId().toString(),
                    "status", "FAILED"
                ));
            }

        } catch (Exception e) {
            log.error("Error processing Cashfree webhook", e);
            throw new RuntimeException("Webhook processing failed", e);
        }
    }
}

package com.fluxpay.api.webhook.controller;

import com.fluxpay.payment.repository.PaymentIntentRepository;
import com.fluxpay.payment.entity.PaymentIntent;
import com.fluxpay.payment.entity.PaymentIntentStatus;
import com.fluxpay.order.repository.OrderRepository;
import com.fluxpay.order.entity.Order;
import com.fluxpay.order.entity.OrderStatus;
import com.fluxpay.webhook.service.WebhookEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks/payu")
@RequiredArgsConstructor
public class PayUWebhookController {

    private final PaymentIntentRepository paymentIntentRepository;
    private final OrderRepository orderRepository;
    private final WebhookEventPublisher webhookEventPublisher;

    @PostMapping
    @Transactional
    public ResponseEntity<String> handlePayUWebhook(@RequestBody Map<String, Object> payload) {
        // In a real application, verify PayU webhook signature here.
        
        // Simulating the payload having an orderId and status
        String orderIdStr = (String) payload.get("txnid");
        String status = (String) payload.get("status");

        if (orderIdStr == null) {
            return ResponseEntity.badRequest().body("Missing txnid");
        }

        UUID orderId = UUID.fromString(orderIdStr);

        if ("success".equalsIgnoreCase(status)) {
            // Update PaymentIntent
            PaymentIntent intent = paymentIntentRepository.findByOrderId(orderId).stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("PaymentIntent not found for order"));
                    
            intent.setStatus(PaymentIntentStatus.CAPTURED);
            paymentIntentRepository.save(intent);

            // Update Order
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);
            
            // Fire outgoing webhook to merchant using Redis Queue
            webhookEventPublisher.publishEvent(order.getMerchantId(), "order.paid", Map.of("orderId", orderId, "amount", order.getTotalAmount()));
        }

        return ResponseEntity.ok("Webhook processed");
    }
}

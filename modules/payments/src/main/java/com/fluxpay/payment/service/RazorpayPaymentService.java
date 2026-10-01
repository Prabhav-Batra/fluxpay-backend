package com.fluxpay.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxpay.external.adapter.RazorpayAdapter;
import com.fluxpay.external.adapter.RazorpaySignatureVerifier;
import com.fluxpay.order.dto.OrderDto;
import com.fluxpay.order.service.OrderService;
import com.fluxpay.payment.dto.RazorpayVerifyRequest;
import com.fluxpay.payment.dto.RazorpayVerifyResponse;
import com.fluxpay.payment.entity.PaymentIntent;
import com.fluxpay.payment.entity.PaymentIntentStatus;
import com.fluxpay.payment.repository.PaymentIntentRepository;
import com.fluxpay.shared.exception.BusinessException;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import com.fluxpay.webhook.service.WebhookEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayPaymentService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final OrderService orderService;
    private final WebhookEventPublisher webhookEventPublisher;
    private final RazorpaySignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;

    /**
     * Called by the checkout page with the three values checkout.js hands to its success handler.
     * The order is only marked paid if the signature proves Razorpay produced them.
     */
    @Transactional
    public RazorpayVerifyResponse verifyCheckoutPayment(RazorpayVerifyRequest request) {
        if (isBlank(request.getRazorpayOrderId()) || isBlank(request.getRazorpayPaymentId()) || isBlank(request.getRazorpaySignature())) {
            throw new BusinessException("razorpay_order_id, razorpay_payment_id and razorpay_signature are required", "MISSING_FIELDS");
        }

        if (!signatureVerifier.isValidPaymentSignature(
                request.getRazorpayOrderId(), request.getRazorpayPaymentId(), request.getRazorpaySignature())) {
            log.warn("Razorpay signature mismatch for order {} / payment {}", request.getRazorpayOrderId(), request.getRazorpayPaymentId());
            throw new BusinessException("Payment signature verification failed", "INVALID_SIGNATURE");
        }

        PaymentIntent intent = findIntent(request.getRazorpayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("PaymentIntent", request.getRazorpayOrderId()));

        markCaptured(intent, request.getRazorpayPaymentId());

        return RazorpayVerifyResponse.builder()
                .paymentIntentId(intent.getId())
                .orderId(intent.getOrderId())
                .status(intent.getStatus())
                .build();
    }

    /**
     * Server-to-server confirmation from Razorpay. Covers customers who close the tab
     * before the checkout page reaches the verify endpoint.
     *
     * @return false if the signature is invalid
     */
    @Transactional
    public boolean handleWebhook(String rawBody, String signature) throws IOException {
        if (!signatureVerifier.isValidWebhookSignature(rawBody, signature)) {
            return false;
        }

        JsonNode root = objectMapper.readTree(rawBody);
        String event = root.path("event").asText();
        JsonNode payment = root.path("payload").path("payment").path("entity");
        String razorpayOrderId = payment.path("order_id").asText(null);
        String razorpayPaymentId = payment.path("id").asText(null);

        if (razorpayOrderId == null) {
            log.info("Razorpay webhook {} has no order id, ignoring", event);
            return true;
        }

        Optional<PaymentIntent> intent = findIntent(razorpayOrderId);
        if (intent.isEmpty()) {
            // Payments made on the same Razorpay account outside Fluxpay land here too
            log.warn("Razorpay webhook {} for unknown order {}, ignoring", event, razorpayOrderId);
            return true;
        }

        switch (event) {
            case "payment.captured", "order.paid" -> markCaptured(intent.get(), razorpayPaymentId);
            case "payment.failed" -> markFailed(intent.get(), payment.path("error_description").asText("Payment failed"));
            default -> log.info("Ignoring Razorpay webhook event {}", event);
        }
        return true;
    }

    private Optional<PaymentIntent> findIntent(String razorpayOrderId) {
        return paymentIntentRepository.findFirstByGatewayProviderAndGatewayReference(RazorpayAdapter.PROVIDER, razorpayOrderId);
    }

    private void markCaptured(PaymentIntent intent, String razorpayPaymentId) {
        if (intent.getStatus() == PaymentIntentStatus.CAPTURED) {
            log.info("PaymentIntent {} already CAPTURED, skipping", intent.getId());
            return;
        }

        intent.setStatus(PaymentIntentStatus.CAPTURED);
        intent.setGatewayPaymentId(razorpayPaymentId);
        intent.setErrorMessage(null);
        paymentIntentRepository.save(intent);
        log.info("PaymentIntent {} marked as CAPTURED", intent.getId());

        OrderDto order = orderService.markPaid(intent.getOrderId());

        // Same event and payload shape the Cashfree flow sent, so existing merchant integrations keep working
        Map<String, Object> payload = new HashMap<>();
        payload.put("payment_intent_id", intent.getId().toString());
        payload.put("order_id", order.getId().toString());
        payload.put("order_reference", order.getOrderReference() != null ? order.getOrderReference() : "");
        payload.put("customer_email", order.getCustomerEmail() != null ? order.getCustomerEmail() : "");
        payload.put("amount", intent.getAmount());
        payload.put("currency", intent.getCurrency());
        payload.put("gateway", RazorpayAdapter.PROVIDER);
        payload.put("gateway_payment_id", razorpayPaymentId);
        payload.put("status", "SUCCESS");
        // Written to the outbox in this transaction; sent only after it commits
        webhookEventPublisher.publishEvent(order.getMerchantId(), "payment.succeeded", payload);
    }

    private void markFailed(PaymentIntent intent, String reason) {
        if (intent.getStatus() == PaymentIntentStatus.CAPTURED) {
            // A failed attempt can arrive after a later attempt on the same order succeeded
            return;
        }

        intent.setStatus(PaymentIntentStatus.FAILED);
        intent.setErrorMessage(reason.length() > 255 ? reason.substring(0, 255) : reason);
        paymentIntentRepository.save(intent);
        log.info("PaymentIntent {} marked as FAILED", intent.getId());

        OrderDto order = orderService.getOrder(intent.getOrderId());
        webhookEventPublisher.publishEvent(order.getMerchantId(), "payment.failed", Map.of(
                "payment_intent_id", intent.getId().toString(),
                "order_id", order.getId().toString(),
                "status", "FAILED"
        ));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

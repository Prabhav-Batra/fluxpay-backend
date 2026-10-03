package com.fluxpay.payments.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxpay.common.error.FluxpayException;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RazorpayWebhookParser {

    private final ObjectMapper objectMapper;

    public RazorpayWebhookParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GatewayWebhookEvent parse(byte[] body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException e) {
            throw malformed();
        }
        if (root == null || !root.isObject()) {
            throw malformed();
        }
        String type = root.path("event").asText("");
        try {
            return switch (type) {
                case "payment.captured" -> new GatewayWebhookEvent.PaymentCaptured(
                        RazorpayPayloads.toPayment(entity(root, "payment")));
                case "payment.failed" -> new GatewayWebhookEvent.PaymentFailed(
                        RazorpayPayloads.toPayment(entity(root, "payment")));
                case "refund.processed" -> new GatewayWebhookEvent.RefundProcessed(
                        RazorpayPayloads.toRefund(entity(root, "refund")));
                default -> new GatewayWebhookEvent.Ignored(type);
            };
        } catch (IllegalArgumentException e) {
            throw malformed();
        }
    }

    private Map<?, ?> entity(JsonNode root, String name) {
        JsonNode entity = root.path("payload").path(name).path("entity");
        if (!entity.isObject()) {
            throw malformed();
        }
        return objectMapper.convertValue(entity, Map.class);
    }

    private static FluxpayException malformed() {
        return FluxpayException.badRequest("MALFORMED_WEBHOOK", "Webhook payload is not a valid Razorpay event");
    }
}

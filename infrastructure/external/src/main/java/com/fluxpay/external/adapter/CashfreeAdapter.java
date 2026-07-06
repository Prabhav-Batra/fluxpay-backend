package com.fluxpay.external.adapter;

import com.fluxpay.external.gateway.PaymentGatewayPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class CashfreeAdapter implements PaymentGatewayPort {

    @Value("${fluxpay.gateways.cashfree.app-id}")
    private String appId;

    @Value("${fluxpay.gateways.cashfree.secret-key}")
    private String secretKey;

    @Value("${fluxpay.gateways.cashfree.environment}")
    private String environment;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String generatePaymentLink(UUID orderId, BigDecimal amount, String currency, String customerEmail) {
        log.info("Generating Cashfree payment session for order: {}", orderId);
        
        String url = "SANDBOX".equalsIgnoreCase(environment) 
            ? "https://sandbox.cashfree.com/pg/orders" 
            : "https://api.cashfree.com/pg/orders";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", appId);
        headers.set("x-client-secret", secretKey);
        headers.set("x-api-version", "2025-01-01");

        Map<String, Object> customerDetails = new HashMap<>();
        // In a real app we'd fetch a real phone number and id, but mocking for now.
        customerDetails.put("customer_id", "cust_" + customerEmail.hashCode());
        customerDetails.put("customer_phone", "9876543210");
        customerDetails.put("customer_email", customerEmail);

        Map<String, Object> orderMeta = new HashMap<>();
        orderMeta.put("return_url", "http://localhost:3002/checkout/" + "SESSION_ID_PLACEHOLDER" + "/success?order_id={order_id}"); // The frontend will handle this or we can pass it dynamically.

        Map<String, Object> body = new HashMap<>();
        body.put("order_amount", amount);
        body.put("order_currency", currency);
        body.put("order_id", orderId.toString());
        body.put("customer_details", customerDetails);
        body.put("order_meta", orderMeta);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String paymentSessionId = (String) response.getBody().get("payment_session_id");
                log.info("Successfully generated Cashfree session: {}", paymentSessionId);
                return paymentSessionId; // We are returning the session ID instead of a link!
            }
        } catch (Exception ex) {
            log.error("Failed to call Cashfree API", ex);
            throw new RuntimeException("Failed to generate Cashfree session", ex);
        }
        
        throw new RuntimeException("Failed to generate Cashfree session: No response body");
    }

    @Override
    public boolean verifyPayment(String paymentReference, String signature) {
        log.info("Verifying Cashfree payment: {}", paymentReference);
        // We will implement verifyPayment via webhook signature separately in WebhookEventConsumer.
        return true;
    }

    @Override
    public String getProviderName() {
        return "CASHFREE";
    }
}

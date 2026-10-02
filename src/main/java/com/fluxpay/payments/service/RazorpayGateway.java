package com.fluxpay.payments.service;

import com.fluxpay.common.error.ErrorType;
import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.service.RazorpayProperties.Credentials;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RazorpayGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayGateway.class);

    private final RestClient client;
    private final RazorpayProperties properties;

    public RazorpayGateway(@Qualifier("razorpayRestClient") RestClient client, RazorpayProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "razorpay";
    }

    @Override
    public boolean isEnabled(Mode mode) {
        return properties.credentials(mode).isPresent();
    }

    @Override
    public String publicKeyId(Mode mode) {
        return credentials(mode).keyId();
    }

    @Override
    public GatewayOrder createOrder(
            Mode mode, long amount, String currency, String receipt, Map<String, String> notes) {
        Credentials credentials = credentials(mode);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("receipt", receipt);
        body.put("notes", notes);
        try {
            Map<?, ?> response = client.post()
                    .uri("/v1/orders")
                    .headers(headers -> authenticate(headers, credentials))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return new GatewayOrder(
                    response.get("id").toString(),
                    ((Number) response.get("amount")).longValue(),
                    response.get("currency").toString());
        } catch (RestClientException | NullPointerException | ClassCastException e) {
            log.error("Razorpay order creation failed for receipt {}", receipt, e);
            throw unavailable();
        }
    }

    @Override
    public List<GatewayPayment> fetchOrderPayments(Mode mode, String orderId) {
        Credentials credentials = credentials(mode);
        try {
            Map<?, ?> response = client.get()
                    .uri("/v1/orders/{orderId}/payments", orderId)
                    .headers(headers -> authenticate(headers, credentials))
                    .retrieve()
                    .body(Map.class);
            List<?> items = (List<?>) response.get("items");
            return items.stream()
                    .map(item -> RazorpayPayloads.toPayment((Map<?, ?>) item))
                    .toList();
        } catch (RestClientException | NullPointerException | ClassCastException | IllegalArgumentException e) {
            log.error("Razorpay payment lookup failed for order {}", orderId, e);
            throw unavailable();
        }
    }

    @Override
    public boolean verifyWebhookSignature(Mode mode, byte[] body, String signature) {
        return properties
                .credentials(mode)
                .map(credentials -> WebhookSignatures.matches(
                        WebhookSignatures.hmacSha256Hex(credentials.webhookSecret(), body), signature))
                .orElse(false);
    }

    private Credentials credentials(Mode mode) {
        return properties
                .credentials(mode)
                .orElseThrow(() -> new FluxpayException(
                        ErrorType.GATEWAY_ERROR,
                        "GATEWAY_NOT_CONFIGURED",
                        "Payments are not configured for " + mode.value() + " mode"));
    }

    private static void authenticate(HttpHeaders headers, Credentials credentials) {
        headers.setBasicAuth(credentials.keyId(), credentials.keySecret());
    }

    private static FluxpayException unavailable() {
        return new FluxpayException(
                ErrorType.GATEWAY_ERROR, "GATEWAY_UNAVAILABLE", "The payment gateway is unavailable, retry shortly");
    }
}

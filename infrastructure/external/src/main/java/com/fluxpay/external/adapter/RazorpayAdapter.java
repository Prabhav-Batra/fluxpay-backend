package com.fluxpay.external.adapter;

import com.fluxpay.external.gateway.GatewayOrder;
import com.fluxpay.external.gateway.PaymentGatewayPort;
import com.fluxpay.shared.exception.PaymentGatewayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Razorpay Standard Checkout: creates a Razorpay Order that the browser then opens
 * with checkout.js. Docs: https://razorpay.com/docs/api/orders/create/
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayAdapter implements PaymentGatewayPort {

    public static final String PROVIDER = "RAZORPAY";
    private static final String ORDERS_URL = "https://api.razorpay.com/v1/orders";
    // Razorpay rejects orders below 100 subunits (e.g. ₹1.00 = 100 paise)
    private static final long MIN_AMOUNT_SUBUNITS = 100;

    @Value("${fluxpay.gateways.razorpay.key-id:}")
    private String keyId;

    @Value("${fluxpay.gateways.razorpay.key-secret:}")
    private String keySecret;

    private final RestTemplate restTemplate;

    /** Public key id - safe to hand to the browser, which needs it to open checkout.js. */
    public String getKeyId() {
        return keyId;
    }

    @Override
    public GatewayOrder createOrder(UUID orderId, BigDecimal amount, String currency, String customerEmail, String returnUrl) {
        if (keyId.isBlank() || keySecret.isBlank()) {
            throw new PaymentGatewayException("Razorpay is not configured", "GATEWAY_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE);
        }

        long amountSubunits = toSubunits(amount, currency);
        if (amountSubunits < MIN_AMOUNT_SUBUNITS) {
            throw new PaymentGatewayException(
                    "Amount must be at least 100 subunits (e.g. 1.00 " + currency + ")", "AMOUNT_TOO_LOW", HttpStatus.BAD_REQUEST);
        }

        log.info("Creating Razorpay order for order: {} ({} {} subunits)", orderId, amountSubunits, currency);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(keyId, keySecret);

        Map<String, Object> notes = new HashMap<>();
        notes.put("fluxpay_order_id", orderId.toString());
        notes.put("customer_email", customerEmail);

        Map<String, Object> body = new HashMap<>();
        body.put("amount", amountSubunits);
        body.put("currency", currency);
        body.put("receipt", orderId.toString()); // max 40 chars; a UUID is 36
        body.put("notes", notes);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(ORDERS_URL, new HttpEntity<>(body, headers), Map.class);
            Object razorpayOrderId = response.getBody() != null ? response.getBody().get("id") : null;
            if (razorpayOrderId == null) {
                throw new PaymentGatewayException("Razorpay returned no order id", "GATEWAY_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            log.info("Created Razorpay order {} for order {}", razorpayOrderId, orderId);
            return new GatewayOrder(razorpayOrderId.toString(), razorpayOrderId.toString());
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 401) {
                log.error("Razorpay rejected the API credentials - check RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET");
                throw new PaymentGatewayException("Payment gateway authentication failed", "GATEWAY_AUTH_FAILED", HttpStatus.UNAUTHORIZED);
            }
            log.error("Razorpay order creation failed: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new PaymentGatewayException("Failed to create Razorpay order", "GATEWAY_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (RestClientException ex) {
            log.error("Could not reach Razorpay", ex);
            throw new PaymentGatewayException("Payment gateway unreachable", "GATEWAY_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** Converts a major-unit amount (e.g. 499.00 INR) into gateway subunits (49900 paise). */
    public static long toSubunits(BigDecimal amount, String currency) {
        int fractionDigits = Math.max(Currency.getInstance(currency).getDefaultFractionDigits(), 0);
        return amount.movePointRight(fractionDigits).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    @Override
    public String getProviderName() {
        return PROVIDER;
    }
}

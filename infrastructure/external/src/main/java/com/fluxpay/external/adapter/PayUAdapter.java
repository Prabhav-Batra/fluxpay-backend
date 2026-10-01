package com.fluxpay.external.adapter;

import com.fluxpay.external.gateway.GatewayOrder;
import com.fluxpay.external.gateway.PaymentGatewayPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Placeholder only - does not call PayU. Disabled unless fluxpay.gateways.payu.enabled=true.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "fluxpay.gateways.payu.enabled", havingValue = "true")
public class PayUAdapter implements PaymentGatewayPort {

    @Override
    public GatewayOrder createOrder(UUID orderId, BigDecimal amount, String currency, String customerEmail, String returnUrl) {
        log.info("Generating PayU payment link for order: {}", orderId);
        // Placeholder for real API call
        return new GatewayOrder("https://pmny.in/" + UUID.randomUUID().toString().substring(0, 8), null);
    }

    @Override
    public String getProviderName() {
        return "PAYU";
    }
}

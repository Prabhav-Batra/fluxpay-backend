package com.fluxpay.external.adapter;

import com.fluxpay.external.gateway.PaymentGatewayPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
public class CashfreeAdapter implements PaymentGatewayPort {

    @Override
    public String generatePaymentLink(UUID orderId, BigDecimal amount, String currency, String customerEmail) {
        log.info("Generating Cashfree payment link for order: {}", orderId);
        // Placeholder for real API call
        return "https://payments.cashfree.com/links/" + UUID.randomUUID().toString();
    }

    @Override
    public boolean verifyPayment(String paymentReference, String signature) {
        log.info("Verifying Cashfree payment: {}", paymentReference);
        // Placeholder for signature verification
        return true;
    }

    @Override
    public String getProviderName() {
        return "CASHFREE";
    }
}

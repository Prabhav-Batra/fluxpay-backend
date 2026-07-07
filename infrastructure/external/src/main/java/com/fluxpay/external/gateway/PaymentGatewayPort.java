package com.fluxpay.external.gateway;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGatewayPort {
    String generatePaymentLink(UUID orderId, BigDecimal amount, String currency, String customerEmail, String returnUrl);
    boolean verifyPayment(String paymentReference, String signature);
    String getProviderName();
}

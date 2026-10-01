package com.fluxpay.external.gateway;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGatewayPort {
    GatewayOrder createOrder(UUID orderId, BigDecimal amount, String currency, String customerEmail, String returnUrl);
    String getProviderName();
}

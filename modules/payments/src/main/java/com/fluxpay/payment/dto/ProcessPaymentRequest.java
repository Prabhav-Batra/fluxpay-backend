package com.fluxpay.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ProcessPaymentRequest {
    @NotNull(message = "Order ID is required")
    private UUID orderId;

    private String preferredGateway; // Optional, e.g. "CASHFREE" or "PAYU"
    
    private String returnUrl;
}

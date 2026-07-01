package com.fluxpay.subscription.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class SubscriptionCreateRequest {
    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;

    @Email(message = "Invalid email format")
    @NotNull(message = "Customer email is required")
    private String customerEmail;

    @NotNull(message = "Product ID is required")
    private UUID productId;
}

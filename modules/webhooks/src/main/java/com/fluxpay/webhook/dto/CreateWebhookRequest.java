package com.fluxpay.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import java.util.UUID;

@Data
public class CreateWebhookRequest {
    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;

    @NotBlank(message = "URL is required")
    @URL(message = "Must be a valid URL")
    private String url;
}

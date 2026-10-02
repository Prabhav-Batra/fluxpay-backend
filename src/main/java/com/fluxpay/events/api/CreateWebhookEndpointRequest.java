package com.fluxpay.events.api;

import jakarta.validation.constraints.NotBlank;

public record CreateWebhookEndpointRequest(@NotBlank String url) {}

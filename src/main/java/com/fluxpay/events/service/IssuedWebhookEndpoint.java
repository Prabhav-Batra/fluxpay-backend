package com.fluxpay.events.service;

/** An endpoint with its signing secret, returned only when the secret is created or rolled. */
public record IssuedWebhookEndpoint(WebhookEndpointView endpoint, String secret) {}

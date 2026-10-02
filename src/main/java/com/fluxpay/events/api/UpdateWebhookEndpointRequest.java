package com.fluxpay.events.api;

public record UpdateWebhookEndpointRequest(String url, Boolean enabled) {}

package com.fluxpay.webhook.entity;

public enum WebhookDeliveryStatus {
    PENDING,   // waiting for first attempt or a retry
    DELIVERED, // merchant answered 2xx
    FAILED     // gave up (dead letter) - kept for inspection / manual replay
}

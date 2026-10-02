package com.fluxpay.events.service;

public interface WebhookDispatcher {

    /** Claims due deliveries, sends them outside any transaction and records each outcome. Returns how many ran. */
    int dispatchDue();
}

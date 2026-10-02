package com.fluxpay.apikeys.service;

/** A newly created key. {@code secret} is shown to the merchant once and never stored. */
public record IssuedApiKey(ApiKeyView key, String secret) {}

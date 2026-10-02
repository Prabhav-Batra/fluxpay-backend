package com.fluxpay.common.ratelimit;

public record RateLimitDecision(boolean allowed, long limit, long remaining, long resetSeconds) {}

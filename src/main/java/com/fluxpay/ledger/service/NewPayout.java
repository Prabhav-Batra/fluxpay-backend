package com.fluxpay.ledger.service;

import java.time.Instant;
import java.util.UUID;

/** {@code paidAt} null means now. */
public record NewPayout(long amount, String reference, Instant paidAt, UUID recordedBy) {}

package com.fluxpay.subscription.dto;

import com.fluxpay.subscription.entity.SubscriptionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class SubscriptionDto {
    private UUID id;
    private UUID merchantId;
    private String customerEmail;
    private UUID productId;
    private SubscriptionStatus status;
    private Instant currentPeriodStart;
    private Instant currentPeriodEnd;
    private boolean cancelAtPeriodEnd;
    private Instant createdAt;
}

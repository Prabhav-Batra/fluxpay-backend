package com.fluxpay.notification.dto;

import com.fluxpay.notification.entity.NotificationType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class UpsertNotificationConfigRequest {
    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;

    @NotNull(message = "Notification type is required")
    private NotificationType notificationType;

    @NotEmpty(message = "At least one channel is required")
    private List<String> channels;
}

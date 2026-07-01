package com.fluxpay.notification.dto;

import com.fluxpay.notification.entity.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class NotificationConfigDto {
    private UUID id;
    private UUID merchantId;
    private NotificationType notificationType;
    private List<String> channels;
    private boolean active;
}

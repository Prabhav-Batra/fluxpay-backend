package com.fluxpay.notification.controller;

import com.fluxpay.notification.dto.NotificationConfigDto;
import com.fluxpay.notification.dto.UpsertNotificationConfigRequest;
import com.fluxpay.notification.entity.NotificationType;
import com.fluxpay.notification.service.NotificationService;
import com.fluxpay.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/config")
    public ResponseEntity<ApiResponse<NotificationConfigDto>> upsertConfig(@Valid @RequestBody UpsertNotificationConfigRequest request) {
        NotificationConfigDto config = notificationService.upsertConfig(request);
        return ResponseEntity.ok(ApiResponse.success(config, "Notification configuration saved"));
    }

    @GetMapping("/config/{merchantId}/{type}")
    public ResponseEntity<ApiResponse<NotificationConfigDto>> getConfig(
            @PathVariable UUID merchantId, @PathVariable NotificationType type) {
        NotificationConfigDto config = notificationService.getConfig(merchantId, type);
        if (config == null) {
            return ResponseEntity.ok(ApiResponse.success(null, "No active config found"));
        }
        return ResponseEntity.ok(ApiResponse.success(config));
    }
}

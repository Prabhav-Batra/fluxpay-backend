package com.fluxpay.notification.service;

import com.fluxpay.notification.dto.NotificationConfigDto;
import com.fluxpay.notification.dto.UpsertNotificationConfigRequest;
import com.fluxpay.notification.entity.NotificationConfig;
import com.fluxpay.notification.entity.NotificationType;
import com.fluxpay.notification.repository.NotificationConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationConfigRepository notificationConfigRepository;

    @Transactional
    public NotificationConfigDto upsertConfig(UpsertNotificationConfigRequest request) {
        Optional<NotificationConfig> existing = notificationConfigRepository
                .findByMerchantIdAndNotificationTypeAndActiveTrue(request.getMerchantId(), request.getNotificationType());

        NotificationConfig config;
        if (existing.isPresent()) {
            config = existing.get();
            config.setChannels(request.getChannels());
        } else {
            config = NotificationConfig.builder()
                    .merchantId(request.getMerchantId())
                    .notificationType(request.getNotificationType())
                    .channels(request.getChannels())
                    .active(true)
                    .build();
        }

        config = notificationConfigRepository.save(config);
        return mapToDto(config);
    }

    @Transactional(readOnly = true)
    public NotificationConfigDto getConfig(UUID merchantId, NotificationType type) {
        return notificationConfigRepository.findByMerchantIdAndNotificationTypeAndActiveTrue(merchantId, type)
                .map(this::mapToDto)
                .orElse(null);
    }

    private NotificationConfigDto mapToDto(NotificationConfig config) {
        return NotificationConfigDto.builder()
                .id(config.getId())
                .merchantId(config.getMerchantId())
                .notificationType(config.getNotificationType())
                .channels(config.getChannels())
                .active(config.isActive())
                .build();
    }
}

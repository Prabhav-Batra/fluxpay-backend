package com.fluxpay.notification.repository;

import com.fluxpay.notification.entity.NotificationConfig;
import com.fluxpay.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationConfigRepository extends JpaRepository<NotificationConfig, UUID> {
    Optional<NotificationConfig> findByMerchantIdAndNotificationTypeAndActiveTrue(UUID merchantId, NotificationType notificationType);
}

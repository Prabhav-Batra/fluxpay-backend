package com.fluxpay.webhook.repository;

import com.fluxpay.webhook.entity.WebhookDelivery;
import com.fluxpay.webhook.entity.WebhookDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    List<WebhookDelivery> findByStatus(WebhookDeliveryStatus status);

    @Modifying
    @Transactional
    @Query("delete from WebhookDelivery d where d.status = :status and d.createdAt < :before")
    int deleteOld(WebhookDeliveryStatus status, Instant before);
}

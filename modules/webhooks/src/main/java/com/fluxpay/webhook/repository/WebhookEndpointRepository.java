package com.fluxpay.webhook.repository;

import com.fluxpay.webhook.entity.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
    List<WebhookEndpoint> findByMerchantIdAndActiveTrue(UUID merchantId);
}

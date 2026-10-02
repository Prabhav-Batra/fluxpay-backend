package com.fluxpay.events.persistence;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.events.domain.WebhookEndpoint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface WebhookEndpointRepository extends Repository<WebhookEndpoint, UUID> {

    WebhookEndpoint save(WebhookEndpoint endpoint);

    Optional<WebhookEndpoint> findById(UUID id);

    Optional<WebhookEndpoint> findByIdAndMerchantIdAndModeAndDeletedAtIsNull(UUID id, UUID merchantId, Mode mode);

    List<WebhookEndpoint> findByMerchantIdAndModeAndDeletedAtIsNullOrderByCreatedAtAsc(UUID merchantId, Mode mode);

    List<WebhookEndpoint> findByMerchantIdAndModeAndEnabledTrueAndDeletedAtIsNull(UUID merchantId, Mode mode);

    long countByMerchantIdAndModeAndDeletedAtIsNull(UUID merchantId, Mode mode);
}

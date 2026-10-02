package com.fluxpay.apikeys.persistence;

import com.fluxpay.apikeys.domain.ApiKey;
import com.fluxpay.common.tenant.Mode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped finders only: every lookup a controller can trigger includes merchant and mode. */
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findByMerchantIdAndModeOrderByCreatedAtDesc(UUID merchantId, Mode mode);

    Optional<ApiKey> findByIdAndMerchantIdAndMode(UUID id, UUID merchantId, Mode mode);

    Optional<ApiKey> findByLookupId(String lookupId);
}

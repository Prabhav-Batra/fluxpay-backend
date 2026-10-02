package com.fluxpay.apikeys.service;

import com.fluxpay.common.tenant.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyService {

    IssuedApiKey create(TenantContext tenant);

    List<ApiKeyView> list(TenantContext tenant);

    /** Revokes the key and issues a replacement. API_KEY_REVOKED (conflict) if already revoked. */
    IssuedApiKey roll(TenantContext tenant, UUID keyId);

    /** Idempotent. API_KEY_NOT_FOUND (not found) when the key is not in this tenant and mode. */
    void revoke(TenantContext tenant, UUID keyId);

    /** Empty for any invalid, unknown or revoked key. MERCHANT_SUSPENDED (forbidden) for suspended merchants. */
    Optional<ApiKeyPrincipal> authenticate(String rawKey);
}

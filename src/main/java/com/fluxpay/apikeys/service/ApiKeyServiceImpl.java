package com.fluxpay.apikeys.service;

import com.fluxpay.apikeys.domain.ApiKey;
import com.fluxpay.apikeys.persistence.ApiKeyRepository;
import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.merchants.service.MerchantService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyServiceImpl implements ApiKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeys;
    private final MerchantService merchantService;
    private final Clock clock;

    public ApiKeyServiceImpl(ApiKeyRepository apiKeys, MerchantService merchantService, Clock clock) {
        this.apiKeys = apiKeys;
        this.merchantService = merchantService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public IssuedApiKey create(TenantContext tenant) {
        ApiKeySecret secret = ApiKeySecret.generate(tenant.mode(), RANDOM);
        ApiKey key = apiKeys.save(new ApiKey(
                tenant.merchantId(), tenant.mode(), secret.lookupId(), secret.sha256Hex(), Instant.now(clock)));
        return new IssuedApiKey(ApiKeyView.from(key), secret.value());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiKeyView> list(TenantContext tenant) {
        return apiKeys.findByMerchantIdAndModeOrderByCreatedAtDesc(tenant.merchantId(), tenant.mode()).stream()
                .map(ApiKeyView::from)
                .toList();
    }

    @Override
    @Transactional
    public IssuedApiKey roll(TenantContext tenant, UUID keyId) {
        ApiKey existing = find(tenant, keyId);
        if (existing.isRevoked()) {
            throw FluxpayException.conflict("API_KEY_REVOKED", "A revoked key cannot be rolled");
        }
        existing.revoke(Instant.now(clock));
        return create(tenant);
    }

    @Override
    @Transactional
    public void revoke(TenantContext tenant, UUID keyId) {
        find(tenant, keyId).revoke(Instant.now(clock));
    }

    @Override
    @Transactional
    public Optional<ApiKeyPrincipal> authenticate(String rawKey) {
        Optional<ApiKeySecret> parsed = ApiKeySecret.parse(rawKey);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        ApiKeySecret secret = parsed.get();
        Optional<ApiKey> stored = apiKeys.findByLookupId(secret.lookupId())
                .filter(key -> secret.matches(key.getSecretHash()))
                .filter(key -> !key.isRevoked())
                .filter(key -> key.getMode() == secret.mode());
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        ApiKey key = stored.get();
        if (!merchantService.isActive(key.getMerchantId())) {
            throw FluxpayException.forbidden("MERCHANT_SUSPENDED", "This merchant account is suspended");
        }
        key.markUsed(Instant.now(clock));
        return Optional.of(new ApiKeyPrincipal(key.getId(), key.getMerchantId(), key.getMode()));
    }

    private ApiKey find(TenantContext tenant, UUID keyId) {
        return apiKeys.findByIdAndMerchantIdAndMode(keyId, tenant.merchantId(), tenant.mode())
                .orElseThrow(() -> FluxpayException.notFound("API_KEY_NOT_FOUND", "API key not found"));
    }
}

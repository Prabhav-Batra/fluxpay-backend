package com.fluxpay.apikeys.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.common.tenant.TenantPrincipal;
import java.util.Optional;
import java.util.UUID;

public record ApiKeyPrincipal(UUID keyId, UUID merchantId, Mode mode) implements TenantPrincipal {

    @Override
    public Optional<Mode> fixedMode() {
        return Optional.of(mode);
    }
}

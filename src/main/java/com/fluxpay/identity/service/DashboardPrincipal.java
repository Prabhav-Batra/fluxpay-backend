package com.fluxpay.identity.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.common.tenant.TenantPrincipal;
import com.fluxpay.identity.domain.Role;
import java.io.Serializable;
import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

/** The signed-in dashboard user, stored in the server session. */
public record DashboardPrincipal(UUID userId, String email, Role role, UUID merchantId)
        implements TenantPrincipal, Principal, Serializable {

    @Override
    public Optional<Mode> fixedMode() {
        return Optional.empty();
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}

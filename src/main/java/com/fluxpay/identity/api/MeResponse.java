package com.fluxpay.identity.api;

import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.identity.domain.Role;
import com.fluxpay.identity.service.DashboardPrincipal;

public record MeResponse(String id, String email, Role role, String merchantId) {

    public static MeResponse from(DashboardPrincipal principal) {
        String merchantId =
                principal.merchantId() == null ? null : PublicId.of(IdPrefix.MERCHANT, principal.merchantId());
        return new MeResponse(
                PublicId.of(IdPrefix.USER, principal.userId()), principal.email(), principal.role(), merchantId);
    }
}

package com.fluxpay.common.tenant;

import java.util.Optional;
import java.util.UUID;

/** Implemented by every authenticated principal that can act for a merchant. */
public interface TenantPrincipal {

    /** The merchant this principal acts for, or {@code null} for platform staff. */
    UUID merchantId();

    /** Present when the credential itself is bound to a mode (API keys); empty for dashboard sessions. */
    Optional<Mode> fixedMode();
}

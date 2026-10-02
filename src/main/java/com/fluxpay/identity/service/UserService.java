package com.fluxpay.identity.service;

import java.util.Locale;
import java.util.UUID;

public interface UserService {

    /**
     * Locks the email for the current transaction and throws EMAIL_TAKEN (conflict) if it is registered.
     * Must be called inside a transaction; serializes concurrent signups for one email.
     */
    void reserveEmail(String email);

    /** Creates the owner login for a new merchant. Throws EMAIL_TAKEN (conflict) when the email exists. */
    DashboardPrincipal createMerchantOwner(String email, String rawPassword, UUID merchantId);

    /** Throws INVALID_CREDENTIALS (unauthenticated) for an unknown email or wrong password. */
    DashboardPrincipal authenticate(String email, String rawPassword);

    /** Creates the platform admin if no user with that email exists; otherwise does nothing. */
    void ensurePlatformAdmin(String email, String rawPassword);

    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}

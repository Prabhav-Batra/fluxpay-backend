package com.fluxpay.checkout.service;

import com.fluxpay.common.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckoutService {

    CheckoutSessionView create(TenantContext tenant, NewCheckoutSession session);

    CheckoutSessionView get(TenantContext tenant, UUID sessionId);

    CheckoutSessionView createFromLink(String slug, String customerRef);

    PublicCheckoutView getPublic(UUID sessionId);

    /** Creates (once) the gateway order for the session. Never holds a transaction during the gateway call. */
    PaymentInstructions startPayment(UUID sessionId);

    /** Row-locks the session owning this gateway order in the caller's transaction. */
    Optional<CheckoutSessionView> lockByGatewayOrderId(String gatewayOrderId);

    String verifyPayment(UUID sessionId, String gatewayOrderId, String gatewayPaymentId, String signature);

    void markCompleted(UUID sessionId, Instant now);

    /** Expires up to {@code limit} open sessions past their expiry in the caller's transaction. */
    List<CheckoutSessionView> expireDue(Instant now, int limit);

    /**
     * Open or expired sessions with a gateway order, created before {@code createdBefore} and expiring no earlier
     * than {@code expiredAfter}, least-recently reconciled first.
     */
    List<CheckoutSessionView> findReconcilable(Instant createdBefore, Instant expiredAfter, int limit);

    void markReconciled(UUID sessionId, Instant now);
}

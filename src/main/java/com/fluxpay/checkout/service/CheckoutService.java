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

    void markCompleted(UUID sessionId, Instant now);

    /** Expires up to {@code limit} open sessions past their expiry in the caller's transaction. */
    List<CheckoutSessionView> expireDue(Instant now, int limit);

    List<CheckoutSessionView> findReconcilable(Instant createdAfter, Instant createdBefore, int limit);
}

package com.fluxpay.checkout.api;

import com.fluxpay.checkout.service.CheckoutService;
import com.fluxpay.checkout.service.NewCheckoutSession;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.idempotency.IdempotencyService;
import com.fluxpay.common.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Merchant backends create checkout sessions with their secret API key (spec §4.1). */
@RestController
@RequestMapping("/api/v1/checkout_sessions")
public class CheckoutSessionApiController {

    private final CheckoutService checkoutService;
    private final IdempotencyService idempotency;

    public CheckoutSessionApiController(CheckoutService checkoutService, IdempotencyService idempotency) {
        this.checkoutService = checkoutService;
        this.idempotency = idempotency;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutSessionResponse create(
            TenantContext tenant,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateCheckoutSessionRequest body) {
        UUID productId =
                PublicId.parseOrNotFound(IdPrefix.PRODUCT, body.productId(), "PRODUCT_NOT_FOUND", "Product not found");
        NewCheckoutSession command = new NewCheckoutSession(
                productId, body.customerRef(), body.successUrl(), body.cancelUrl(), body.metadata());
        return idempotency.execute(
                tenant,
                idempotencyKey,
                body,
                CheckoutSessionResponse.class,
                () -> CheckoutSessionResponse.from(checkoutService.create(tenant, command)));
    }

    @GetMapping("/{id}")
    public CheckoutSessionResponse get(TenantContext tenant, @PathVariable String id) {
        return CheckoutSessionResponse.from(checkoutService.get(tenant, parseId(id)));
    }

    static UUID parseId(String id) {
        return PublicId.parseOrNotFound(
                IdPrefix.CHECKOUT_SESSION, id, "CHECKOUT_SESSION_NOT_FOUND", "Checkout session not found");
    }
}

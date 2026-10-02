package com.fluxpay.checkout.api;

import com.fluxpay.checkout.service.CheckoutService;
import com.fluxpay.checkout.service.CheckoutSessionView;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated endpoints used by the hosted checkout pages. Rate-limited by config. */
@RestController
@RequestMapping("/api/v1/public")
public class PublicCheckoutController {

    private final CheckoutService checkoutService;

    public PublicCheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @GetMapping("/checkout_sessions/{id}")
    public PublicCheckoutResponse get(@PathVariable String id) {
        return PublicCheckoutResponse.from(checkoutService.getPublic(CheckoutSessionApiController.parseId(id)));
    }

    @PostMapping("/checkout_sessions/{id}/pay")
    public PaymentInstructionsResponse pay(@PathVariable String id) {
        return PaymentInstructionsResponse.from(checkoutService.startPayment(CheckoutSessionApiController.parseId(id)));
    }

    @PostMapping("/payment_links/{slug}/checkout_sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public LinkCheckoutResponse createFromLink(
            @PathVariable String slug, @Valid @RequestBody(required = false) LinkCheckoutRequest body) {
        String ref = body == null ? null : body.ref();
        CheckoutSessionView session = checkoutService.createFromLink(slug, ref);
        return new LinkCheckoutResponse(PublicId.of(IdPrefix.CHECKOUT_SESSION, session.id()), session.url());
    }
}

package com.fluxpay.catalog.api;

import com.fluxpay.catalog.service.NewPaymentLink;
import com.fluxpay.catalog.service.PaymentLinkChanges;
import com.fluxpay.catalog.service.PaymentLinkService;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard/payment_links")
public class PaymentLinkController {

    private final PaymentLinkService paymentLinkService;

    public PaymentLinkController(PaymentLinkService paymentLinkService) {
        this.paymentLinkService = paymentLinkService;
    }

    @GetMapping
    public CursorPage<PaymentLinkResponse> list(
            TenantContext tenant,
            @RequestParam(name = "starting_after", required = false) String startingAfter,
            @RequestParam(required = false) Integer limit) {
        PageQuery query = PageQuery.of(IdPrefix.PAYMENT_LINK, startingAfter, limit);
        return paymentLinkService.list(tenant, query).map(PaymentLinkResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentLinkResponse create(TenantContext tenant, @Valid @RequestBody CreatePaymentLinkRequest body) {
        UUID productId = DashboardProductController.parseId(body.productId());
        return PaymentLinkResponse.from(
                paymentLinkService.create(tenant, new NewPaymentLink(productId, body.successUrl(), body.cancelUrl())));
    }

    @GetMapping("/{id}")
    public PaymentLinkResponse get(TenantContext tenant, @PathVariable String id) {
        return PaymentLinkResponse.from(paymentLinkService.get(tenant, parseId(id)));
    }

    @PatchMapping("/{id}")
    public PaymentLinkResponse update(
            TenantContext tenant, @PathVariable String id, @Valid @RequestBody UpdatePaymentLinkRequest body) {
        PaymentLinkChanges changes = new PaymentLinkChanges(body.successUrl(), body.cancelUrl(), body.active());
        return PaymentLinkResponse.from(paymentLinkService.update(tenant, parseId(id), changes));
    }

    private static UUID parseId(String id) {
        return PublicId.parseOrNotFound(IdPrefix.PAYMENT_LINK, id, "PAYMENT_LINK_NOT_FOUND", "Payment link not found");
    }
}

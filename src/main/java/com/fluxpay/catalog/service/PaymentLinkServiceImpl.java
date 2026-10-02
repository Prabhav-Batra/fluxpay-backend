package com.fluxpay.catalog.service;

import com.fluxpay.catalog.domain.PaymentLink;
import com.fluxpay.catalog.persistence.PaymentLinkRepository;
import com.fluxpay.common.config.FluxpayProperties;
import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.Base62;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.common.web.RedirectUrlPolicy;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentLinkServiceImpl implements PaymentLinkService {

    static final int SLUG_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentLinkRepository links;
    private final ProductService productService;
    private final FluxpayProperties properties;
    private final Clock clock;

    public PaymentLinkServiceImpl(
            PaymentLinkRepository links, ProductService productService, FluxpayProperties properties, Clock clock) {
        this.links = links;
        this.productService = productService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PaymentLinkView create(TenantContext tenant, NewPaymentLink link) {
        productService.get(tenant, link.productId());
        RedirectUrlPolicy.validate(tenant.mode(), "success_url", link.successUrl());
        RedirectUrlPolicy.validate(tenant.mode(), "cancel_url", link.cancelUrl());
        PaymentLink saved = links.save(new PaymentLink(
                tenant.merchantId(),
                tenant.mode(),
                link.productId(),
                uniqueSlug(),
                blankToNull(link.successUrl()),
                blankToNull(link.cancelUrl()),
                Instant.now(clock)));
        return view(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentLinkView get(TenantContext tenant, UUID linkId) {
        return view(find(tenant, linkId));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<PaymentLinkView> list(TenantContext tenant, PageQuery query) {
        return CursorPage.from(
                links.page(tenant.merchantId(), tenant.mode(), query.before(), Limit.of(query.fetchSize())),
                query,
                this::view,
                view -> PublicId.of(IdPrefix.PAYMENT_LINK, view.id()));
    }

    @Override
    @Transactional
    public PaymentLinkView update(TenantContext tenant, UUID linkId, PaymentLinkChanges changes) {
        PaymentLink link = find(tenant, linkId);
        Instant now = Instant.now(clock);
        if (changes.successUrl() != null || changes.cancelUrl() != null) {
            String success = changes.successUrl() == null ? link.getSuccessUrl() : blankToNull(changes.successUrl());
            String cancel = changes.cancelUrl() == null ? link.getCancelUrl() : blankToNull(changes.cancelUrl());
            RedirectUrlPolicy.validate(tenant.mode(), "success_url", success);
            RedirectUrlPolicy.validate(tenant.mode(), "cancel_url", cancel);
            link.changeRedirects(success, cancel, now);
        }
        if (changes.active() != null) {
            link.setActive(changes.active(), now);
        }
        return view(link);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentLinkView> findActiveBySlug(String slug) {
        return links.findBySlugAndActiveTrue(slug).map(this::view);
    }

    private PaymentLink find(TenantContext tenant, UUID linkId) {
        return links.findByIdAndMerchantIdAndMode(linkId, tenant.merchantId(), tenant.mode())
                .orElseThrow(() -> FluxpayException.notFound("PAYMENT_LINK_NOT_FOUND", "Payment link not found"));
    }

    private String uniqueSlug() {
        String slug = Base62.random(SLUG_LENGTH, RANDOM);
        while (links.existsBySlug(slug)) {
            slug = Base62.random(SLUG_LENGTH, RANDOM);
        }
        return slug;
    }

    private PaymentLinkView view(PaymentLink link) {
        return new PaymentLinkView(
                link.getId(),
                link.getMerchantId(),
                link.getMode(),
                link.getProductId(),
                link.getSlug(),
                properties.frontendBaseUrl() + "/l/" + link.getSlug(),
                link.getSuccessUrl(),
                link.getCancelUrl(),
                link.isActive(),
                link.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

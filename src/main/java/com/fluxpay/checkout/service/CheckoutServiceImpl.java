package com.fluxpay.checkout.service;

import com.fluxpay.catalog.service.PaymentLinkService;
import com.fluxpay.catalog.service.PaymentLinkView;
import com.fluxpay.catalog.service.ProductService;
import com.fluxpay.catalog.service.ProductView;
import com.fluxpay.checkout.domain.CheckoutSession;
import com.fluxpay.checkout.domain.CheckoutStatus;
import com.fluxpay.checkout.persistence.CheckoutSessionRepository;
import com.fluxpay.common.config.FluxpayProperties;
import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.common.validation.Metadata;
import com.fluxpay.common.web.RedirectUrlPolicy;
import com.fluxpay.merchants.service.MerchantService;
import com.fluxpay.merchants.service.MerchantView;
import com.fluxpay.payments.service.GatewayOrder;
import com.fluxpay.payments.service.PaymentGateway;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CheckoutServiceImpl implements CheckoutService {

    private final CheckoutSessionRepository sessions;
    private final ProductService productService;
    private final PaymentLinkService paymentLinkService;
    private final MerchantService merchantService;
    private final PaymentGateway gateway;
    private final CheckoutProperties checkoutProperties;
    private final FluxpayProperties fluxpayProperties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public CheckoutServiceImpl(
            CheckoutSessionRepository sessions,
            ProductService productService,
            PaymentLinkService paymentLinkService,
            MerchantService merchantService,
            PaymentGateway gateway,
            CheckoutProperties checkoutProperties,
            FluxpayProperties fluxpayProperties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.sessions = sessions;
        this.productService = productService;
        this.paymentLinkService = paymentLinkService;
        this.merchantService = merchantService;
        this.gateway = gateway;
        this.checkoutProperties = checkoutProperties;
        this.fluxpayProperties = fluxpayProperties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional
    public CheckoutSessionView create(TenantContext tenant, NewCheckoutSession command) {
        ProductView product = productService.get(tenant, command.productId());
        if (!product.active()) {
            throw FluxpayException.conflict("PRODUCT_INACTIVE", "This product is archived");
        }
        RedirectUrlPolicy.validate(tenant.mode(), "success_url", command.successUrl());
        RedirectUrlPolicy.validate(tenant.mode(), "cancel_url", command.cancelUrl());
        return view(save(
                tenant,
                product,
                null,
                command.customerRef(),
                command.successUrl(),
                command.cancelUrl(),
                Metadata.validated(command.metadata())));
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutSessionView get(TenantContext tenant, UUID sessionId) {
        return sessions.findByIdAndMerchantIdAndMode(sessionId, tenant.merchantId(), tenant.mode())
                .map(this::view)
                .orElseThrow(CheckoutServiceImpl::sessionNotFound);
    }

    @Override
    @Transactional
    public CheckoutSessionView createFromLink(String slug, String customerRef) {
        PaymentLinkView link = paymentLinkService.findActiveBySlug(slug).orElseThrow(CheckoutServiceImpl::linkNotFound);
        if (!merchantService.isActive(link.merchantId())) {
            throw linkNotFound();
        }
        TenantContext tenant = new TenantContext(link.merchantId(), link.mode());
        ProductView product = productService.get(tenant, link.productId());
        if (!product.active()) {
            throw linkNotFound();
        }
        return view(save(
                tenant,
                product,
                link.id(),
                customerRef,
                link.successUrl(),
                link.cancelUrl(),
                Metadata.validated(null)));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicCheckoutView getPublic(UUID sessionId) {
        CheckoutSession session = sessions.findById(sessionId).orElseThrow(CheckoutServiceImpl::sessionNotFound);
        MerchantView merchant = merchantService.get(session.getMerchantId());
        ProductView product = productService.get(
                new TenantContext(session.getMerchantId(), session.getMode()), session.getProductId());
        CheckoutStatus status = session.getStatus() == CheckoutStatus.OPEN && !session.isPayableAt(Instant.now(clock))
                ? CheckoutStatus.EXPIRED
                : session.getStatus();
        return new PublicCheckoutView(
                session.getId(),
                session.getMode(),
                status,
                session.getAmount(),
                session.getCurrency(),
                product.name(),
                product.description(),
                product.imageUrl(),
                merchant.businessName(),
                merchant.logoUrl(),
                merchant.brandColor(),
                status == CheckoutStatus.COMPLETED ? session.getSuccessUrl() : null,
                session.getCancelUrl(),
                session.getExpiresAt());
    }

    @Override
    public PaymentInstructions startPayment(UUID sessionId) {
        CheckoutSession session = sessions.findById(sessionId).orElseThrow(CheckoutServiceImpl::sessionNotFound);
        if (session.getStatus() == CheckoutStatus.COMPLETED) {
            throw FluxpayException.conflict("SESSION_COMPLETED", "This checkout session is already paid");
        }
        if (!session.isPayableAt(Instant.now(clock))) {
            throw FluxpayException.conflict("SESSION_EXPIRED", "This checkout session has expired");
        }
        if (!merchantService.isActive(session.getMerchantId())) {
            throw FluxpayException.conflict("MERCHANT_UNAVAILABLE", "This merchant cannot accept payments");
        }
        String orderId = session.getGatewayOrderId() != null ? session.getGatewayOrderId() : attachNewOrder(session);
        MerchantView merchant = merchantService.get(session.getMerchantId());
        ProductView product = productService.get(
                new TenantContext(session.getMerchantId(), session.getMode()), session.getProductId());
        return new PaymentInstructions(
                gateway.name(),
                gateway.publicKeyId(session.getMode()),
                orderId,
                session.getAmount(),
                session.getCurrency(),
                merchant.businessName(),
                product.name());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<CheckoutSessionView> lockByGatewayOrderId(String gatewayOrderId) {
        return sessions.lockByGatewayOrderId(gatewayOrderId).map(this::view);
    }

    @Override
    @Transactional
    public String verifyPayment(UUID sessionId, String gatewayOrderId, String gatewayPaymentId, String signature) {
        CheckoutSession session = sessions.findById(sessionId).orElseThrow(CheckoutServiceImpl::sessionNotFound);
        if (!gateway.verifyPaymentSignature(session.getMode(), gatewayOrderId, gatewayPaymentId, signature)) {
            throw FluxpayException.conflict("INVALID_SIGNATURE", "Payment signature verification failed");
        }
        // Do not complete the session here; rely on the webhook to create the sale.
        return session.getSuccessUrl();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markCompleted(UUID sessionId, Instant now) {
        sessions.findById(sessionId)
                .orElseThrow(CheckoutServiceImpl::sessionNotFound)
                .complete(now);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<CheckoutSessionView> expireDue(Instant now, int limit) {
        List<CheckoutSession> due = sessions.lockDueForExpiry(CheckoutStatus.OPEN, now, Limit.of(limit));
        due.forEach(CheckoutSession::expire);
        return due.stream().map(this::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckoutSessionView> findReconcilable(Instant createdBefore, Instant expiredAfter, int limit) {
        return sessions
                .findReconcilable(
                        List.of(CheckoutStatus.OPEN, CheckoutStatus.EXPIRED),
                        createdBefore,
                        expiredAfter,
                        Limit.of(limit))
                .stream()
                .map(this::view)
                .toList();
    }

    @Override
    @Transactional
    public void markReconciled(UUID sessionId, Instant now) {
        sessions.markReconciled(sessionId, now);
    }

    /** Gateway call happens outside any transaction; the conditional update makes concurrent calls converge. */
    private String attachNewOrder(CheckoutSession session) {
        String publicId = PublicId.of(IdPrefix.CHECKOUT_SESSION, session.getId());
        GatewayOrder order = gateway.createOrder(
                session.getMode(),
                session.getAmount(),
                session.getCurrency(),
                publicId,
                Map.of(
                        "checkout_session_id",
                        publicId,
                        "merchant_id",
                        PublicId.of(IdPrefix.MERCHANT, session.getMerchantId())));
        Integer attached = transaction.execute(status -> sessions.attachOrder(session.getId(), order.id()));
        if (attached != null && attached == 1) {
            return order.id();
        }
        return sessions.findById(session.getId())
                .map(CheckoutSession::getGatewayOrderId)
                .orElseThrow(CheckoutServiceImpl::sessionNotFound);
    }

    private CheckoutSession save(
            TenantContext tenant,
            ProductView product,
            UUID paymentLinkId,
            String customerRef,
            String successUrl,
            String cancelUrl,
            Map<String, String> metadata) {
        Instant now = Instant.now(clock);
        return sessions.save(new CheckoutSession(
                tenant.merchantId(),
                tenant.mode(),
                product.id(),
                paymentLinkId,
                product.amount(),
                product.currency(),
                customerRef,
                successUrl,
                cancelUrl,
                metadata,
                now.plus(checkoutProperties.sessionTtl()),
                now));
    }

    private CheckoutSessionView view(CheckoutSession session) {
        return new CheckoutSessionView(
                session.getId(),
                session.getMerchantId(),
                session.getMode(),
                session.getProductId(),
                session.getPaymentLinkId(),
                session.getAmount(),
                session.getCurrency(),
                session.getCustomerRef(),
                session.getSuccessUrl(),
                session.getCancelUrl(),
                Map.copyOf(session.getMetadata()),
                session.getStatus(),
                session.getGatewayOrderId(),
                fluxpayProperties.frontendBaseUrl() + "/pay/" + PublicId.of(IdPrefix.CHECKOUT_SESSION, session.getId()),
                session.getExpiresAt(),
                session.getCompletedAt(),
                session.getCreatedAt());
    }

    private static FluxpayException sessionNotFound() {
        return FluxpayException.notFound("CHECKOUT_SESSION_NOT_FOUND", "Checkout session not found");
    }

    private static FluxpayException linkNotFound() {
        return FluxpayException.notFound("PAYMENT_LINK_NOT_FOUND", "Payment link not found");
    }
}

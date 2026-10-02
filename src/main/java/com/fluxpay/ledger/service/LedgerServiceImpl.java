package com.fluxpay.ledger.service;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.ledger.domain.LedgerEntry;
import com.fluxpay.ledger.domain.LedgerEntryType;
import com.fluxpay.ledger.domain.Payout;
import com.fluxpay.ledger.persistence.LedgerEntryRepository;
import com.fluxpay.ledger.persistence.PayoutRepository;
import com.fluxpay.merchants.service.MerchantService;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerServiceImpl implements LedgerService {

    static final String CURRENCY = "INR";

    private final LedgerEntryRepository entries;
    private final PayoutRepository payouts;
    private final MerchantService merchantService;
    private final Clock clock;

    public LedgerServiceImpl(
            LedgerEntryRepository entries, PayoutRepository payouts, MerchantService merchantService, Clock clock) {
        this.entries = entries;
        this.payouts = payouts;
        this.merchantService = merchantService;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public SaleEntries recordSale(TenantContext tenant, UUID saleId, long gross, String currency, long gatewayFee) {
        long platformFee = FeeCalculator.platformFee(
                gross, merchantService.get(tenant.merchantId()).platformFeeBps());
        Instant now = Instant.now(clock);
        save(tenant, LedgerEntryType.SALE_GROSS, gross, currency, saleId, null, now);
        if (platformFee > 0) {
            save(tenant, LedgerEntryType.PLATFORM_FEE, -platformFee, currency, saleId, null, now);
        }
        if (gatewayFee > 0) {
            save(tenant, LedgerEntryType.GATEWAY_FEE, -gatewayFee, currency, saleId, null, now);
        }
        return new SaleEntries(platformFee, gatewayFee, gross - platformFee - gatewayFee);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordRefund(
            TenantContext tenant, UUID saleId, String gatewayRefundId, long amount, String currency) {
        if (entries.existsByTypeAndReference(LedgerEntryType.REFUND, gatewayRefundId)) {
            return false;
        }
        save(tenant, LedgerEntryType.REFUND, -amount, currency, saleId, gatewayRefundId, Instant.now(clock));
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Balance balance(TenantContext tenant) {
        Map<LedgerEntryType, Long> totals = new EnumMap<>(LedgerEntryType.class);
        for (Object[] row : entries.totalsByType(tenant.merchantId(), tenant.mode())) {
            totals.put((LedgerEntryType) row[0], ((Number) row[1]).longValue());
        }
        long available = totals.values().stream().mapToLong(Long::longValue).sum();
        return new Balance(
                totals.getOrDefault(LedgerEntryType.SALE_GROSS, 0L),
                totals.getOrDefault(LedgerEntryType.PLATFORM_FEE, 0L),
                totals.getOrDefault(LedgerEntryType.GATEWAY_FEE, 0L),
                totals.getOrDefault(LedgerEntryType.REFUND, 0L),
                totals.getOrDefault(LedgerEntryType.PAYOUT, 0L),
                available,
                CURRENCY);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<LedgerEntryView> entries(TenantContext tenant, PageQuery query) {
        return CursorPage.from(
                entries.page(tenant.merchantId(), tenant.mode(), query.before(), Limit.of(query.fetchSize())),
                query,
                LedgerEntryView::from,
                view -> PublicId.of(IdPrefix.LEDGER_ENTRY, view.id()));
    }

    @Override
    @Transactional
    public PayoutView recordPayout(TenantContext tenant, NewPayout request) {
        merchantService.get(tenant.merchantId());
        payouts.lockBalance(tenant.merchantId().toString(), tenant.mode().name());
        if (payouts.existsByMerchantIdAndModeAndReference(tenant.merchantId(), tenant.mode(), request.reference())) {
            throw FluxpayException.conflict("DUPLICATE_PAYOUT", "A payout with this reference is already recorded");
        }
        if (request.amount() > balance(tenant).available()) {
            throw FluxpayException.conflict("INSUFFICIENT_BALANCE", "Payout exceeds the available balance");
        }
        Instant now = Instant.now(clock);
        Payout payout = payouts.save(new Payout(
                tenant.merchantId(),
                tenant.mode(),
                request.amount(),
                CURRENCY,
                request.reference(),
                request.paidAt() == null ? now : request.paidAt(),
                request.recordedBy(),
                now));
        save(tenant, LedgerEntryType.PAYOUT, -request.amount(), CURRENCY, null, null, now);
        return PayoutView.from(payout);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<PayoutView> payouts(TenantContext tenant, PageQuery query) {
        return CursorPage.from(
                payouts.page(tenant.merchantId(), tenant.mode(), query.before(), Limit.of(query.fetchSize())),
                query,
                PayoutView::from,
                view -> PublicId.of(IdPrefix.PAYOUT, view.id()));
    }

    private void save(
            TenantContext tenant,
            LedgerEntryType type,
            long amount,
            String currency,
            UUID saleId,
            String reference,
            Instant now) {
        entries.save(
                new LedgerEntry(tenant.merchantId(), tenant.mode(), type, amount, currency, saleId, reference, now));
    }
}

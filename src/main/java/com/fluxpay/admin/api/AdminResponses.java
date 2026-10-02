package com.fluxpay.admin.api;

import com.fluxpay.admin.service.AdminMerchantDetail;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.ledger.api.BalanceResponse;
import com.fluxpay.ledger.service.PayoutView;
import com.fluxpay.merchants.api.MerchantResponse;
import java.time.Instant;

public final class AdminResponses {

    private AdminResponses() {}

    public record Balances(BalanceResponse test, BalanceResponse live) {}

    public record MerchantDetail(MerchantResponse merchant, Balances balances) {

        static MerchantDetail from(AdminMerchantDetail detail) {
            return new MerchantDetail(
                    MerchantResponse.from(detail.merchant()),
                    new Balances(
                            BalanceResponse.from(detail.testBalance()), BalanceResponse.from(detail.liveBalance())));
        }
    }

    public record Payout(
            String id,
            Mode mode,
            long amount,
            String currency,
            String reference,
            Instant paidAt,
            String recordedBy,
            Instant createdAt) {

        static Payout from(PayoutView view) {
            return new Payout(
                    PublicId.of(IdPrefix.PAYOUT, view.id()),
                    view.mode(),
                    view.amount(),
                    view.currency(),
                    view.reference(),
                    view.paidAt(),
                    PublicId.of(IdPrefix.USER, view.recordedBy()),
                    view.createdAt());
        }
    }
}

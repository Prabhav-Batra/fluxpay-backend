package com.fluxpay.analytics.api;

import com.fluxpay.analytics.service.AnalyticsViews;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import java.time.LocalDate;
import java.util.List;

public final class AnalyticsResponses {

    static final String CURRENCY = "INR";

    private AnalyticsResponses() {}

    public record SummaryResponse(
            LocalDate from,
            LocalDate to,
            String currency,
            long grossSales,
            long refunds,
            long platformFees,
            long gatewayFees,
            long net,
            long salesCount) {

        static SummaryResponse from(AnalyticsViews.Summary summary) {
            return new SummaryResponse(
                    summary.range().from(),
                    summary.range().to(),
                    CURRENCY,
                    summary.grossSales(),
                    summary.refunds(),
                    summary.platformFees(),
                    summary.gatewayFees(),
                    summary.net(),
                    summary.salesCount());
        }
    }

    public record DayResponse(LocalDate date, long revenue, long salesCount) {}

    public record TimeseriesResponse(LocalDate from, LocalDate to, String currency, List<DayResponse> data) {

        static TimeseriesResponse from(AnalyticsViews.Timeseries series) {
            return new TimeseriesResponse(
                    series.range().from(),
                    series.range().to(),
                    CURRENCY,
                    series.points().stream()
                            .map(p -> new DayResponse(p.date(), p.revenue(), p.salesCount()))
                            .toList());
        }
    }

    public record ProductResponse(String productId, long units, long revenue) {

        static ProductResponse from(AnalyticsViews.ProductPerformance product) {
            return new ProductResponse(
                    PublicId.of(IdPrefix.PRODUCT, product.productId()), product.units(), product.revenue());
        }
    }

    public record TopProductsResponse(String currency, List<ProductResponse> data) {}
}

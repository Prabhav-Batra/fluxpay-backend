package com.fluxpay.analytics.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AnalyticsViews {

    private AnalyticsViews() {}

    public record Summary(
            DateRange range,
            long grossSales,
            long refunds,
            long platformFees,
            long gatewayFees,
            long net,
            long salesCount) {}

    public record DayPoint(LocalDate date, long revenue, long salesCount) {}

    public record Timeseries(DateRange range, List<DayPoint> points) {}

    public record ProductPerformance(UUID productId, long units, long revenue) {}
}

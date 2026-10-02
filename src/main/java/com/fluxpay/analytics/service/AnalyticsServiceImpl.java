package com.fluxpay.analytics.service;

import com.fluxpay.analytics.persistence.AnalyticsRepository;
import com.fluxpay.analytics.persistence.AnalyticsRepository.DayRow;
import com.fluxpay.analytics.persistence.AnalyticsRepository.SalesTotals;
import com.fluxpay.analytics.service.AnalyticsViews.DayPoint;
import com.fluxpay.analytics.service.AnalyticsViews.ProductPerformance;
import com.fluxpay.analytics.service.AnalyticsViews.Summary;
import com.fluxpay.analytics.service.AnalyticsViews.Timeseries;
import com.fluxpay.common.tenant.TenantContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private final AnalyticsRepository repository;
    private final AnalyticsProperties properties;
    private final Clock clock;

    public AnalyticsServiceImpl(AnalyticsRepository repository, AnalyticsProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Summary summary(TenantContext tenant, LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        SalesTotals sales = repository.salesTotals(tenant, range.startInstant(), range.endInstant());
        Map<String, Long> ledger = repository.ledgerTotals(tenant, range.startInstant(), range.endInstant());
        long gross = ledger.getOrDefault("SALE_GROSS", 0L);
        long refunds = -ledger.getOrDefault("REFUND", 0L);
        long platformFees = -ledger.getOrDefault("PLATFORM_FEE", 0L);
        long gatewayFees = -ledger.getOrDefault("GATEWAY_FEE", 0L);
        return new Summary(
                range,
                gross,
                refunds,
                platformFees,
                gatewayFees,
                gross - refunds - platformFees - gatewayFees,
                sales.count());
    }

    @Override
    @Transactional(readOnly = true)
    public Timeseries timeseries(TenantContext tenant, LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        Map<LocalDate, DayRow> byDay =
                repository.salesByDay(tenant, range.startInstant(), range.endInstant(), range.zone()).stream()
                        .collect(Collectors.toMap(DayRow::date, Function.identity()));
        List<DayPoint> points = new ArrayList<>();
        for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
            DayRow row = byDay.get(day);
            points.add(row == null ? new DayPoint(day, 0, 0) : new DayPoint(day, row.amount(), row.count()));
        }
        return new Timeseries(range, points);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductPerformance> topProducts(TenantContext tenant, LocalDate from, LocalDate to, int limit) {
        DateRange range = range(from, to);
        return repository.topProducts(tenant, range.startInstant(), range.endInstant(), limit).stream()
                .map(row -> new ProductPerformance(row.productId(), row.units(), row.revenue()))
                .toList();
    }

    private DateRange range(LocalDate from, LocalDate to) {
        return DateRange.of(from, to, LocalDate.now(clock.withZone(properties.timezone())), properties.timezone());
    }
}

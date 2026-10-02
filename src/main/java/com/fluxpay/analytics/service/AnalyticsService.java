package com.fluxpay.analytics.service;

import com.fluxpay.analytics.service.AnalyticsViews.ProductPerformance;
import com.fluxpay.analytics.service.AnalyticsViews.Summary;
import com.fluxpay.analytics.service.AnalyticsViews.Timeseries;
import com.fluxpay.common.tenant.TenantContext;
import java.time.LocalDate;
import java.util.List;

public interface AnalyticsService {

    /** Null dates default to the last 30 days; INVALID_RANGE (400) for reversed or over-long ranges. */
    Summary summary(TenantContext tenant, LocalDate from, LocalDate to);

    Timeseries timeseries(TenantContext tenant, LocalDate from, LocalDate to);

    List<ProductPerformance> topProducts(TenantContext tenant, LocalDate from, LocalDate to, int limit);
}

package com.fluxpay.analytics.api;

import com.fluxpay.analytics.api.AnalyticsResponses.ProductResponse;
import com.fluxpay.analytics.api.AnalyticsResponses.SummaryResponse;
import com.fluxpay.analytics.api.AnalyticsResponses.TimeseriesResponse;
import com.fluxpay.analytics.api.AnalyticsResponses.TopProductsResponse;
import com.fluxpay.analytics.service.AnalyticsService;
import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.tenant.TenantContext;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard/analytics")
public class AnalyticsController {

    static final int MAX_TOP_PRODUCTS = 20;

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public SummaryResponse summary(
            TenantContext tenant,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return SummaryResponse.from(analyticsService.summary(tenant, from, to));
    }

    @GetMapping("/timeseries")
    public TimeseriesResponse timeseries(
            TenantContext tenant,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return TimeseriesResponse.from(analyticsService.timeseries(tenant, from, to));
    }

    @GetMapping("/top_products")
    public TopProductsResponse topProducts(
            TenantContext tenant,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "5") int limit) {
        if (limit < 1 || limit > MAX_TOP_PRODUCTS) {
            throw FluxpayException.badRequest("INVALID_LIMIT", "limit must be between 1 and " + MAX_TOP_PRODUCTS);
        }
        return new TopProductsResponse(
                AnalyticsResponses.CURRENCY,
                analyticsService.topProducts(tenant, from, to, limit).stream()
                        .map(ProductResponse::from)
                        .toList());
    }
}

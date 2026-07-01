package com.fluxpay.analytics.controller;

import com.fluxpay.analytics.dto.MerchantAnalyticsDto;
import com.fluxpay.analytics.service.AnalyticsService;
import com.fluxpay.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<MerchantAnalyticsDto>> getMerchantAnalytics(@PathVariable UUID merchantId) {
        MerchantAnalyticsDto analytics = analyticsService.getMerchantAnalytics(merchantId);
        return ResponseEntity.ok(ApiResponse.success(analytics));
    }
}

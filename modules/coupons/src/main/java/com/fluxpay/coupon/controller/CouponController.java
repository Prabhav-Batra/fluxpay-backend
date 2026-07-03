package com.fluxpay.coupon.controller;

import com.fluxpay.coupon.dto.ApplyCouponRequest;
import com.fluxpay.coupon.dto.CouponDto;
import com.fluxpay.coupon.dto.CreateCouponRequest;
import com.fluxpay.coupon.service.CouponService;
import com.fluxpay.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping
    public ResponseEntity<ApiResponse<CouponDto>> createCoupon(@Valid @RequestBody CreateCouponRequest request) {
        CouponDto coupon = couponService.createCoupon(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(coupon, "Coupon created successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CouponDto>> getCoupon(@PathVariable UUID id) {
        CouponDto coupon = couponService.getCoupon(id);
        return ResponseEntity.ok(ApiResponse.success(coupon));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<CouponDto>>> getAllCoupons(@PathVariable UUID merchantId) {
        List<CouponDto> coupons = couponService.getAllCoupons(merchantId);
        return ResponseEntity.ok(ApiResponse.success(coupons));
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<CouponDto>> applyCoupon(@Valid @RequestBody ApplyCouponRequest request) {
        CouponDto coupon = couponService.applyCoupon(request);
        return ResponseEntity.ok(ApiResponse.success(coupon, "Coupon applied successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateCoupon(@PathVariable UUID id) {
        couponService.deactivateCoupon(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Coupon deactivated successfully"));
    }
}

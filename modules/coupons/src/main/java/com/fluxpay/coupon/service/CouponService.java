package com.fluxpay.coupon.service;

import com.fluxpay.coupon.dto.ApplyCouponRequest;
import com.fluxpay.coupon.dto.CouponDto;
import com.fluxpay.coupon.dto.CreateCouponRequest;
import com.fluxpay.coupon.entity.Coupon;
import com.fluxpay.coupon.repository.CouponRepository;
import com.fluxpay.shared.exception.BusinessException;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    @Transactional
    public CouponDto createCoupon(CreateCouponRequest request) {
        Coupon coupon = Coupon.builder()
                .merchantId(request.getMerchantId())
                .code(request.getCode().toUpperCase())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .maxUses(request.getMaxUses())
                .validUntil(request.getValidUntil())
                .active(true)
                .build();

        coupon = couponRepository.save(coupon);
        return mapToDto(coupon);
    }

    @Transactional(readOnly = true)
    public CouponDto getCoupon(UUID id) {
        return couponRepository.findById(id)
                .map(this::mapToDto)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", id.toString()));
    }

    @Transactional(readOnly = true)
    public List<CouponDto> getAllCoupons(UUID merchantId) {
        return couponRepository.findByMerchantId(merchantId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public CouponDto applyCoupon(ApplyCouponRequest request) {
        Coupon coupon = couponRepository.findByCodeAndMerchantId(request.getCode().toUpperCase(), request.getMerchantId())
                .orElseThrow(() -> new BusinessException("Coupon not found or invalid", "INVALID_COUPON"));

        if (!coupon.isActive()) {
            throw new BusinessException("Coupon is no longer active", "COUPON_INACTIVE");
        }

        if (coupon.getValidUntil() != null && Instant.now().isAfter(coupon.getValidUntil())) {
            throw new BusinessException("Coupon has expired", "COUPON_EXPIRED");
        }

        if (coupon.getMaxUses() != null && coupon.getTimesUsed() >= coupon.getMaxUses()) {
            throw new BusinessException("Coupon usage limit reached", "COUPON_LIMIT_REACHED");
        }

        coupon.incrementUsage();
        coupon = couponRepository.save(coupon);
        
        return mapToDto(coupon);
    }

    @Transactional
    public void deactivateCoupon(UUID id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", id.toString()));
        coupon.setActive(false);
        couponRepository.save(coupon);
    }

    private CouponDto mapToDto(Coupon coupon) {
        return CouponDto.builder()
                .id(coupon.getId())
                .merchantId(coupon.getMerchantId())
                .code(coupon.getCode())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .maxUses(coupon.getMaxUses())
                .timesUsed(coupon.getTimesUsed())
                .validUntil(coupon.getValidUntil())
                .active(coupon.isActive())
                .createdAt(coupon.getCreatedAt())
                .build();
    }
}

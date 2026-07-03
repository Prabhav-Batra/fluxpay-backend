package com.fluxpay.coupon.repository;

import com.fluxpay.coupon.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, UUID> {
    Optional<Coupon> findByCodeAndMerchantId(String code, UUID merchantId);
    List<Coupon> findByMerchantId(UUID merchantId);
}

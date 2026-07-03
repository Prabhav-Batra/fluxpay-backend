package com.fluxpay.subscription.repository;

import com.fluxpay.subscription.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    List<Subscription> findByMerchantIdAndCustomerEmail(UUID merchantId, String customerEmail);
    List<Subscription> findByMerchantId(UUID merchantId);
}

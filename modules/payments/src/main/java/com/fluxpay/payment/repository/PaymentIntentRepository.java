package com.fluxpay.payment.repository;

import com.fluxpay.payment.entity.PaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {
    List<PaymentIntent> findByOrderId(UUID orderId);
    List<PaymentIntent> findByOrderIdIn(List<UUID> orderIds);
}

package com.fluxpay.payment.repository;

import com.fluxpay.payment.entity.PaymentIntent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {
    List<PaymentIntent> findByOrderId(UUID orderId);
    List<PaymentIntent> findByOrderIdIn(List<UUID> orderIds);

    // Row lock so the browser callback and the gateway webhook can't both process the same payment
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentIntent> findFirstByGatewayProviderAndGatewayReference(String gatewayProvider, String gatewayReference);
}

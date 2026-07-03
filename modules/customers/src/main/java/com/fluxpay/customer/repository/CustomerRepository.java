package com.fluxpay.customer.repository;

import com.fluxpay.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    List<Customer> findByMerchantId(UUID merchantId);
    Optional<Customer> findByMerchantIdAndEmail(UUID merchantId, String email);
}

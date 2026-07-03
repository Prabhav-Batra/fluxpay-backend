package com.fluxpay.customer.service;

import com.fluxpay.customer.dto.CustomerDto;
import com.fluxpay.customer.entity.Customer;
import com.fluxpay.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public List<CustomerDto> getCustomersByMerchant(UUID merchantId) {
        return customerRepository.findByMerchantId(merchantId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private CustomerDto mapToDto(Customer customer) {
        return CustomerDto.builder()
                .id(customer.getId())
                .merchantId(customer.getMerchantId())
                .email(customer.getEmail())
                .name(customer.getName())
                .status(customer.getStatus())
                .totalSpent(customer.getTotalSpent())
                .joinDate(customer.getJoinDate())
                .build();
    }
}

package com.fluxpay.customer.controller;

import com.fluxpay.customer.dto.CustomerDto;
import com.fluxpay.customer.service.CustomerService;
import com.fluxpay.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<CustomerDto>>> getCustomersByMerchant(@PathVariable UUID merchantId) {
        List<CustomerDto> customers = customerService.getCustomersByMerchant(merchantId);
        return ResponseEntity.ok(ApiResponse.success(customers));
    }
}

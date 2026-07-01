package com.fluxpay.merchant.controller;

import com.fluxpay.merchant.dto.MerchantCreateRequest;
import com.fluxpay.merchant.dto.MerchantDto;
import com.fluxpay.merchant.service.MerchantService;
import com.fluxpay.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @PostMapping
    public ResponseEntity<ApiResponse<MerchantDto>> createMerchant(@Valid @RequestBody MerchantCreateRequest request) {
        MerchantDto merchant = merchantService.createMerchant(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(merchant, "Merchant created successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MerchantDto>> getMerchant(@PathVariable UUID id) {
        MerchantDto merchant = merchantService.getMerchant(id);
        return ResponseEntity.ok(ApiResponse.success(merchant));
    }
}

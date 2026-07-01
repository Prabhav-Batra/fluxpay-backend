package com.fluxpay.organization.controller;

import com.fluxpay.organization.dto.OrganizationCreateRequest;
import com.fluxpay.organization.dto.OrganizationDto;
import com.fluxpay.organization.service.OrganizationService;
import com.fluxpay.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrganizationDto>> createOrganization(@Valid @RequestBody OrganizationCreateRequest request) {
        OrganizationDto organization = organizationService.createOrganization(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(organization, "Organization created successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrganizationDto>> getOrganization(@PathVariable UUID id) {
        OrganizationDto organization = organizationService.getOrganization(id);
        return ResponseEntity.ok(ApiResponse.success(organization));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<OrganizationDto>>> getOrganizationsByMerchant(@PathVariable UUID merchantId) {
        List<OrganizationDto> organizations = organizationService.getOrganizationsByMerchant(merchantId);
        return ResponseEntity.ok(ApiResponse.success(organizations));
    }
}

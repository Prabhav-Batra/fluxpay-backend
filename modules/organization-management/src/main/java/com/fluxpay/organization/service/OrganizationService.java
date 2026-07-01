package com.fluxpay.organization.service;

import com.fluxpay.organization.dto.OrganizationCreateRequest;
import com.fluxpay.organization.dto.OrganizationDto;
import com.fluxpay.organization.entity.Organization;
import com.fluxpay.organization.repository.OrganizationRepository;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    @Transactional
    public OrganizationDto createOrganization(OrganizationCreateRequest request) {
        Organization organization = Organization.builder()
                .name(request.getName())
                .merchantId(request.getMerchantId())
                .build();

        organization = organizationRepository.save(organization);
        return mapToDto(organization);
    }

    @Transactional(readOnly = true)
    public OrganizationDto getOrganization(UUID id) {
        return organizationRepository.findById(id)
                .map(this::mapToDto)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id.toString()));
    }

    @Transactional(readOnly = true)
    public List<OrganizationDto> getOrganizationsByMerchant(UUID merchantId) {
        return organizationRepository.findByMerchantId(merchantId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private OrganizationDto mapToDto(Organization organization) {
        return OrganizationDto.builder()
                .id(organization.getId())
                .name(organization.getName())
                .merchantId(organization.getMerchantId())
                .createdAt(organization.getCreatedAt())
                .build();
    }
}

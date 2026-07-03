package com.fluxpay.organization.service;

import com.fluxpay.organization.dto.OrganizationCreateRequest;
import com.fluxpay.organization.dto.OrganizationDto;
import com.fluxpay.organization.entity.Organization;
import com.fluxpay.organization.entity.Application;
import com.fluxpay.organization.repository.OrganizationRepository;
import com.fluxpay.organization.repository.ApplicationRepository;
import com.fluxpay.organization.dto.ApplicationCreateRequest;
import com.fluxpay.organization.dto.ApplicationDto;
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
    private final ApplicationRepository applicationRepository;

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

    @Transactional
    public ApplicationDto createApplication(ApplicationCreateRequest request) {
        Organization org = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization", request.getOrganizationId().toString()));

        Application application = Application.builder()
                .name(request.getName())
                .organizationId(org.getId())
                .build();

        application = applicationRepository.save(application);
        return mapToApplicationDto(application);
    }

    @Transactional(readOnly = true)
    public List<ApplicationDto> getApplicationsByOrganization(UUID organizationId) {
        return applicationRepository.findByOrganizationId(organizationId).stream()
                .map(this::mapToApplicationDto)
                .collect(Collectors.toList());
    }

    private ApplicationDto mapToApplicationDto(Application application) {
        return ApplicationDto.builder()
                .id(application.getId())
                .name(application.getName())
                .organizationId(application.getOrganizationId())
                .createdAt(application.getCreatedAt())
                .build();
    }
}

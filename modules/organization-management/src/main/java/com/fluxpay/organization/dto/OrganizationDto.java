package com.fluxpay.organization.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class OrganizationDto {
    private UUID id;
    private String name;
    private UUID merchantId;
    private Instant createdAt;
}

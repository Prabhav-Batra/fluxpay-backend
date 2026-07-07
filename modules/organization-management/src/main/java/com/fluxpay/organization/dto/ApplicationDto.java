package com.fluxpay.organization.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class ApplicationDto {
    private UUID id;
    private String name;
    private UUID organizationId;
    private Instant createdAt;
}

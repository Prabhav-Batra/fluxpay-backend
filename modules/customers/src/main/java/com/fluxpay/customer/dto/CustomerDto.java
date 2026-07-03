package com.fluxpay.customer.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CustomerDto {
    private UUID id;
    private UUID merchantId;
    private String email;
    private String name;
    private String status;
    private BigDecimal totalSpent;
    private Instant joinDate;
}

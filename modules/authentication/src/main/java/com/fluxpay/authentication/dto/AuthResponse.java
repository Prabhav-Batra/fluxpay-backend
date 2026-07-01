package com.fluxpay.authentication.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AuthResponse {
    private String token;
    private UUID merchantId;
    private String email;
}

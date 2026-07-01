package com.fluxpay.authentication.service;

import com.fluxpay.authentication.dto.AuthResponse;
import com.fluxpay.authentication.dto.LoginRequest;
import com.fluxpay.merchant.entity.Merchant;
import com.fluxpay.merchant.service.MerchantService;
import com.fluxpay.shared.exception.BusinessException;
import com.fluxpay.shared.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final MerchantService merchantService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthResponse login(LoginRequest request) {
        Merchant merchant = merchantService.getMerchantEntityByEmail(request.getEmail());

        if (!passwordEncoder.matches(request.getPassword(), merchant.getPasswordHash())) {
            throw new BusinessException("Invalid email or password", "INVALID_CREDENTIALS");
        }

        String token = jwtUtil.generateToken(
                merchant.getEmail(),
                Map.of("merchantId", merchant.getId().toString())
        );

        return AuthResponse.builder()
                .token(token)
                .merchantId(merchant.getId())
                .email(merchant.getEmail())
                .build();
    }
}

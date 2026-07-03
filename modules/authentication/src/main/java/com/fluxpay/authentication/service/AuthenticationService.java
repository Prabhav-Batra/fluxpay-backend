package com.fluxpay.authentication.service;

import com.fluxpay.authentication.dto.AuthResponse;
import com.fluxpay.authentication.dto.LoginRequest;
import com.fluxpay.authentication.dto.RegisterRequest;
import com.fluxpay.merchant.dto.MerchantCreateRequest;
import com.fluxpay.merchant.dto.MerchantDto;
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

    public AuthResponse register(RegisterRequest request) {
        MerchantCreateRequest createRequest = new MerchantCreateRequest();
        createRequest.setEmail(request.getEmail());
        createRequest.setPassword(request.getPassword());
        createRequest.setMetadata(Map.of());

        MerchantDto merchant = merchantService.createMerchant(createRequest);

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

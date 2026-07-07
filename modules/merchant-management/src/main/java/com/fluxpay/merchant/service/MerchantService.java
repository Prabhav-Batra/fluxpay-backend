package com.fluxpay.merchant.service;

import com.fluxpay.merchant.dto.MerchantCreateRequest;
import com.fluxpay.merchant.dto.MerchantDto;
import com.fluxpay.merchant.entity.Merchant;
import com.fluxpay.merchant.repository.MerchantRepository;
import com.fluxpay.shared.exception.BusinessException;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MerchantDto createMerchant(MerchantCreateRequest request) {
        if (merchantRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Merchant with this email already exists", "MERCHANT_ALREADY_EXISTS");
        }

        Merchant merchant = Merchant.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .metadata(request.getMetadata())
                .build();

        merchant = merchantRepository.save(merchant);
        return mapToDto(merchant);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "merchants", key = "#id.toString()")
    public MerchantDto getMerchant(UUID id) {
        return merchantRepository.findById(id)
                .map(this::mapToDto)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", id.toString()));
    }

    @Transactional(readOnly = true)
    public MerchantDto verifyCredentials(String email, String rawPassword) {
        Merchant merchant = merchantRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Invalid email or password", "INVALID_CREDENTIALS"));
                
        if (!passwordEncoder.matches(rawPassword, merchant.getPasswordHash())) {
            throw new BusinessException("Invalid email or password", "INVALID_CREDENTIALS");
        }
        
        return mapToDto(merchant);
    }

    private MerchantDto mapToDto(Merchant merchant) {
        return MerchantDto.builder()
                .id(merchant.getId())
                .email(merchant.getEmail())
                .metadata(merchant.getMetadata())
                .createdAt(merchant.getCreatedAt())
                .build();
    }
}

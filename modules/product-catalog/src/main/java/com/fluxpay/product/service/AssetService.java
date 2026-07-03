package com.fluxpay.product.service;

import com.fluxpay.product.dto.AssetDto;
import com.fluxpay.product.dto.CreateAssetRequest;
import com.fluxpay.product.entity.Asset;
import com.fluxpay.product.repository.AssetRepository;
import com.fluxpay.shared.exception.BusinessException;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;

    @Transactional
    public AssetDto createAsset(CreateAssetRequest request) {
        if (assetRepository.findByInternalKeyAndMerchantId(request.getInternalKey(), request.getMerchantId()).isPresent()) {
            throw new BusinessException("Asset with this internal key already exists for the merchant", "ASSET_KEY_EXISTS");
        }

        Asset asset = Asset.builder()
                .merchantId(request.getMerchantId())
                .name(request.getName())
                .internalKey(request.getInternalKey())
                .description(request.getDescription())
                .assetType(request.getAssetType())
                .defaultValue(request.getDefaultValue())
                .maxValue(request.getMaxValue())
                .deliveryMethod(request.getDeliveryMethod())
                .displayIcon(request.getDisplayIcon())
                .displayColor(request.getDisplayColor())
                .metadata(request.getMetadata())
                .active(true)
                .build();

        asset = assetRepository.save(asset);
        return mapToDto(asset);
    }

    @Transactional(readOnly = true)
    public List<AssetDto> getAssetsByMerchant(UUID merchantId) {
        return assetRepository.findByMerchantId(merchantId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AssetDto getAsset(UUID id) {
        return assetRepository.findById(id)
                .map(this::mapToDto)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", id.toString()));
    }

    private AssetDto mapToDto(Asset asset) {
        return AssetDto.builder()
                .id(asset.getId())
                .merchantId(asset.getMerchantId())
                .name(asset.getName())
                .internalKey(asset.getInternalKey())
                .description(asset.getDescription())
                .assetType(asset.getAssetType())
                .defaultValue(asset.getDefaultValue())
                .maxValue(asset.getMaxValue())
                .deliveryMethod(asset.getDeliveryMethod())
                .displayIcon(asset.getDisplayIcon())
                .displayColor(asset.getDisplayColor())
                .metadata(asset.getMetadata())
                .active(asset.isActive())
                .createdAt(asset.getCreatedAt())
                .build();
    }
}

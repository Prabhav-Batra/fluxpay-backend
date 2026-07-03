package com.fluxpay.product.controller;

import com.fluxpay.product.dto.AssetDto;
import com.fluxpay.product.dto.CreateAssetRequest;
import com.fluxpay.product.service.AssetService;
import com.fluxpay.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @PostMapping
    public ResponseEntity<ApiResponse<AssetDto>> createAsset(@Valid @RequestBody CreateAssetRequest request) {
        AssetDto asset = assetService.createAsset(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(asset, "Asset created successfully"));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<AssetDto>>> getMerchantAssets(@PathVariable UUID merchantId) {
        List<AssetDto> assets = assetService.getAssetsByMerchant(merchantId);
        return ResponseEntity.ok(ApiResponse.success(assets));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetDto>> getAsset(@PathVariable UUID id) {
        AssetDto asset = assetService.getAsset(id);
        return ResponseEntity.ok(ApiResponse.success(asset));
    }
}

package com.fluxpay.product.service;

import com.fluxpay.product.dto.ProductCreateRequest;
import com.fluxpay.product.dto.ProductDto;
import com.fluxpay.product.dto.ProductAssetLinkRequest;
import com.fluxpay.product.entity.Asset;
import com.fluxpay.product.entity.Product;
import com.fluxpay.product.entity.ProductAssetLink;
import com.fluxpay.product.repository.AssetRepository;
import com.fluxpay.product.repository.ProductAssetLinkRepository;
import com.fluxpay.product.repository.ProductRepository;
import com.fluxpay.shared.exception.BusinessException;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final AssetRepository assetRepository;
    private final ProductAssetLinkRepository productAssetLinkRepository;

    @Transactional
    @CacheEvict(value = "merchant-products", key = "#request.merchantId")
    public ProductDto createProduct(ProductCreateRequest request) {
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .currency(request.getCurrency().toUpperCase())
                .merchantId(request.getMerchantId())
                .productType(request.getProductType())
                .billingCycle(request.getBillingCycle())
                .trialPeriodDays(request.getTrialPeriodDays())
                .gracePeriodDays(request.getGracePeriodDays())
                .renewalRules(request.getRenewalRules())
                .cancellationRules(request.getCancellationRules())
                .benefits(request.getBenefits())
                .baseCredits(request.getBaseCredits())
                .bonusCredits(request.getBonusCredits())
                .purchaseLimit(request.getPurchaseLimit())
                .visibility(request.getVisibility())
                .displayImage(request.getDisplayImage())
                .hostedCheckoutUrl(generateHostedCheckoutUrl())
                .metadata(request.getMetadata())
                .active(true)
                .build();

        product = productRepository.save(product);

        // Process Asset Links
        if (request.getAssets() != null && !request.getAssets().isEmpty()) {
            for (ProductAssetLinkRequest linkReq : request.getAssets()) {
                Asset asset = assetRepository.findById(linkReq.getAssetId())
                        .orElseThrow(() -> new BusinessException("Asset not found: " + linkReq.getAssetId(), "ASSET_NOT_FOUND"));
                
                if (!asset.getMerchantId().equals(request.getMerchantId())) {
                    throw new BusinessException("Asset does not belong to this merchant", "INVALID_ASSET_OWNER");
                }

                ProductAssetLink link = ProductAssetLink.builder()
                        .product(product)
                        .asset(asset)
                        .quantityGranted(linkReq.getQuantity())
                        .build();
                productAssetLinkRepository.save(link);
            }
        }

        return mapToDto(product);
    }

    private String generateHostedCheckoutUrl() {
        return "https://checkout.fluxpay.com/pay/" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#id")
    public ProductDto getProduct(UUID id) {
        return productRepository.findById(id)
                .map(this::mapToDto)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id.toString()));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "merchant-products", key = "#merchantId")
    public List<ProductDto> getActiveProductsByMerchant(UUID merchantId) {
        return productRepository.findByMerchantIdAndActiveTrue(merchantId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "products", key = "#id"),
        @CacheEvict(value = "merchant-products", allEntries = true)
    })
    public void deactivateProduct(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id.toString()));
        product.setActive(false);
        productRepository.save(product);
    }

    private ProductDto mapToDto(Product product) {
        return ProductDto.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .merchantId(product.getMerchantId())
                .productType(product.getProductType())
                .billingCycle(product.getBillingCycle())
                .trialPeriodDays(product.getTrialPeriodDays())
                .gracePeriodDays(product.getGracePeriodDays())
                .renewalRules(product.getRenewalRules())
                .cancellationRules(product.getCancellationRules())
                .benefits(product.getBenefits())
                .baseCredits(product.getBaseCredits())
                .bonusCredits(product.getBonusCredits())
                .purchaseLimit(product.getPurchaseLimit())
                .visibility(product.getVisibility())
                .displayImage(product.getDisplayImage())
                .hostedCheckoutUrl(product.getHostedCheckoutUrl())
                .metadata(product.getMetadata())
                .active(product.isActive())
                .createdAt(product.getCreatedAt())
                .build();
    }
}

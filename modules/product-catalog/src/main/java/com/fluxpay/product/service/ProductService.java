package com.fluxpay.product.service;

import com.fluxpay.product.dto.ProductCreateRequest;
import com.fluxpay.product.dto.ProductDto;
import com.fluxpay.product.entity.Product;
import com.fluxpay.product.repository.ProductRepository;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductDto createProduct(ProductCreateRequest request) {
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .currency(request.getCurrency().toUpperCase())
                .merchantId(request.getMerchantId())
                .metadata(request.getMetadata())
                .active(true)
                .build();

        product = productRepository.save(product);
        return mapToDto(product);
    }

    @Transactional(readOnly = true)
    public ProductDto getProduct(UUID id) {
        return productRepository.findById(id)
                .map(this::mapToDto)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id.toString()));
    }

    @Transactional(readOnly = true)
    public List<ProductDto> getActiveProductsByMerchant(UUID merchantId) {
        return productRepository.findByMerchantIdAndActiveTrue(merchantId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
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
                .metadata(product.getMetadata())
                .active(product.isActive())
                .createdAt(product.getCreatedAt())
                .build();
    }
}

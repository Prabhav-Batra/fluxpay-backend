package com.fluxpay.product.controller;

import com.fluxpay.product.dto.ProductCreateRequest;
import com.fluxpay.product.dto.ProductDto;
import com.fluxpay.product.service.ProductService;
import com.fluxpay.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProductDto>> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        ProductDto product = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(product, "Product created successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDto>> getProduct(@PathVariable UUID id) {
        ProductDto product = productService.getProduct(id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<ProductDto>>> getProductsByMerchant(@PathVariable UUID merchantId) {
        List<ProductDto> products = productService.getActiveProductsByMerchant(merchantId);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateProduct(@PathVariable UUID id) {
        productService.deactivateProduct(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Product deactivated successfully"));
    }
}

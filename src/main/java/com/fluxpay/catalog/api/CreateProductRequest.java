package com.fluxpay.catalog.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateProductRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @Size(max = 500) @Pattern(regexp = "^https://\\S+$", message = "must be an https URL") String imageUrl,
        @NotNull @Min(100) @Max(50_000_000) Long amount,
        String currency,
        Map<String, String> metadata) {}

package com.fluxpay.catalog.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record UpdateProductRequest(
        @Size(min = 1, max = 120) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        @Size(max = 1000) String description,
        @Size(max = 500) @Pattern(regexp = "^$|^https://\\S+$", message = "must be an https URL") String imageUrl,
        @Min(100) @Max(50_000_000) Long amount,
        Map<String, String> metadata,
        Boolean active) {}

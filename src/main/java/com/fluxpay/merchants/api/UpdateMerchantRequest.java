package com.fluxpay.merchants.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMerchantRequest(
        @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String businessName,
        @Size(max = 500) @Pattern(regexp = "^$|^https://\\S+$", message = "must be an https URL") String logoUrl,
        @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$", message = "must be a hex colour like #FF3366") String brandColor) {}

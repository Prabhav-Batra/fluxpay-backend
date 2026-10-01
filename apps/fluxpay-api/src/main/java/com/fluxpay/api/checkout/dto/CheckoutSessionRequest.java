package com.fluxpay.api.checkout.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSessionRequest {

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotBlank(message = "Customer email is required")
    @Email(message = "Invalid email format")
    private String customerEmail;

    @NotBlank(message = "Success URL is required")
    @Pattern(regexp = "^https?://.+", message = "Success URL must be an http(s) URL")
    private String successUrl;

    @NotBlank(message = "Cancel URL is required")
    @Pattern(regexp = "^https?://.+", message = "Cancel URL must be an http(s) URL")
    private String cancelUrl;

    private String merchantReference;
}

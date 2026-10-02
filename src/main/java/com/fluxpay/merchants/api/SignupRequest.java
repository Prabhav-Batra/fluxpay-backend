package com.fluxpay.merchants.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Password rules are enforced by PasswordPolicy so byte length is checked, not just characters. */
public record SignupRequest(
        @NotBlank @Size(max = 100) String businessName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull String password) {

    /** Trims the email before validation so " Owner@x.com " is treated as the same address. */
    public SignupRequest {
        email = email == null ? null : email.trim();
    }
}

package com.fluxpay.admin.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AdminRequests {

    private AdminRequests() {}

    public record UpdateMerchant(
            @Min(0) @Max(10_000) Integer platformFeeBps,
            @Pattern(regexp = "active|suspended", message = "must be active or suspended") String status) {}

    /** {@code mode} defaults to live; {@code paidAt} defaults to now. */
    public record RecordPayout(
            @Pattern(regexp = "test|live", message = "must be test or live") String mode,
            @NotNull @Min(1) Long amount,
            @NotBlank @Size(max = 100) String reference,
            Instant paidAt) {}
}

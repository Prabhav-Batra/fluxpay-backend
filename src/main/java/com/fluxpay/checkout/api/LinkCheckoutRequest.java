package com.fluxpay.checkout.api;

import jakarta.validation.constraints.Size;

/** {@code ref} is the merchant's customer reference, passed as {@code ?ref=} on the payment link. */
public record LinkCheckoutRequest(@Size(max = 255) String ref) {}

package com.fluxpay.sales.service;

public interface CheckoutExpiryService {

    /** Expires due open sessions and emits checkout.expired for each. Returns how many expired. */
    int expireDue();
}

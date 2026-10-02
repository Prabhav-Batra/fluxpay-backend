package com.fluxpay.merchants.service;

import java.util.UUID;

public interface MerchantService {

    MerchantView get(UUID merchantId);

    MerchantView updateProfile(UUID merchantId, ProfileUpdate update);

    boolean isActive(UUID merchantId);
}

package com.fluxpay.merchants.service;

import com.fluxpay.identity.service.DashboardPrincipal;

public interface MerchantOnboardingService {

    /** Creates a merchant and its owner login atomically. */
    DashboardPrincipal signUp(SignupCommand command);
}

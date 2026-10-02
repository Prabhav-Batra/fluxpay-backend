package com.fluxpay.apikeys.api;

import com.fluxpay.common.tenant.Mode;

public record AccountResponse(String merchantId, String businessName, Mode mode) {}

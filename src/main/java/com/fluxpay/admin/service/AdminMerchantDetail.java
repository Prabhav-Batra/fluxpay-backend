package com.fluxpay.admin.service;

import com.fluxpay.ledger.service.Balance;
import com.fluxpay.merchants.service.MerchantView;

public record AdminMerchantDetail(MerchantView merchant, Balance testBalance, Balance liveBalance) {}

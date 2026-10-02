package com.fluxpay.apikeys.api;

import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.merchants.service.MerchantService;
import com.fluxpay.merchants.service.MerchantView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets an integration verify which merchant and mode its API key belongs to. */
@RestController
public class AccountController {

    private final MerchantService merchantService;

    public AccountController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping("/api/v1/account")
    public AccountResponse account(TenantContext tenant) {
        MerchantView merchant = merchantService.get(tenant.merchantId());
        return new AccountResponse(
                PublicId.of(IdPrefix.MERCHANT, merchant.id()), merchant.businessName(), tenant.mode());
    }
}

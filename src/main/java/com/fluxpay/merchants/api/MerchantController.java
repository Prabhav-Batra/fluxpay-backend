package com.fluxpay.merchants.api;

import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.merchants.service.MerchantService;
import com.fluxpay.merchants.service.ProfileUpdate;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard/merchant")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping
    public MerchantResponse get(TenantContext tenant) {
        return MerchantResponse.from(merchantService.get(tenant.merchantId()));
    }

    @PatchMapping
    public MerchantResponse update(TenantContext tenant, @Valid @RequestBody UpdateMerchantRequest body) {
        ProfileUpdate update = new ProfileUpdate(body.businessName(), body.logoUrl(), body.brandColor());
        return MerchantResponse.from(merchantService.updateProfile(tenant.merchantId(), update));
    }
}

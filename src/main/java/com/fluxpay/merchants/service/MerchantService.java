package com.fluxpay.merchants.service;

import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.merchants.domain.MerchantStatus;
import java.util.UUID;

public interface MerchantService {

    MerchantView get(UUID merchantId);

    MerchantView updateProfile(UUID merchantId, ProfileUpdate update);

    boolean isActive(UUID merchantId);

    /** All merchants, newest first. Platform admin only. */
    CursorPage<MerchantView> list(PageQuery query);

    /** Null leaves a field unchanged. Platform admin only. */
    MerchantView updateByAdmin(UUID merchantId, Integer platformFeeBps, MerchantStatus status);
}

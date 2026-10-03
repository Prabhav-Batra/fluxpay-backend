package com.fluxpay.sales.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.service.GatewayPayment;

public interface SaleCaptureService {

    /** Records the sale for a captured payment. Safe to call any number of times for the same payment. */
    void capture(Mode mode, GatewayPayment payment);

    /** Records a failed payment attempt. Safe to call any number of times for the same payment. */
    void failPayment(Mode mode, GatewayPayment payment);
}

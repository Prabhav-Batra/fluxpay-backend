package com.fluxpay.sales.service;

import com.fluxpay.payments.service.RecordedPayment;

public record SaleDetailView(SaleView sale, RecordedPayment payment) {}

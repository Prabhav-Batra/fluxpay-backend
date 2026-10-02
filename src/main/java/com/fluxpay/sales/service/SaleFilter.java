package com.fluxpay.sales.service;

import com.fluxpay.sales.domain.SaleStatus;
import java.util.UUID;

public record SaleFilter(String customerRef, UUID productId, SaleStatus status) {}

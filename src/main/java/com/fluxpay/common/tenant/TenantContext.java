package com.fluxpay.common.tenant;

import java.util.UUID;

/** The merchant and mode every tenant-scoped operation runs under. Never built from request input. */
public record TenantContext(UUID merchantId, Mode mode) {}

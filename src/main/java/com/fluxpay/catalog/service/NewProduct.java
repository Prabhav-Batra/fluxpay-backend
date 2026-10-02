package com.fluxpay.catalog.service;

import java.util.Map;

public record NewProduct(
        String name, String description, String imageUrl, long amount, String currency, Map<String, String> metadata) {}

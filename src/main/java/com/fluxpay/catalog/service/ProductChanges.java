package com.fluxpay.catalog.service;

import java.util.Map;

/** Null leaves a field unchanged; an empty description or image URL clears it. */
public record ProductChanges(
        String name, String description, String imageUrl, Long amount, Map<String, String> metadata, Boolean active) {}

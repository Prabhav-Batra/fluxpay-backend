package com.fluxpay.merchants.service;

/** Null means "leave unchanged"; an empty string clears logo or colour. */
public record ProfileUpdate(String businessName, String logoUrl, String brandColor) {}

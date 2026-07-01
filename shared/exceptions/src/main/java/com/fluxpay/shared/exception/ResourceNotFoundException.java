package com.fluxpay.shared.exception;

public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String resource, String id) {
        super(String.format("%s with ID %s not found", resource, id), "RESOURCE_NOT_FOUND");
    }
}

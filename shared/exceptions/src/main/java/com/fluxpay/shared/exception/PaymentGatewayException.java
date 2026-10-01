package com.fluxpay.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Raised when an upstream payment gateway (Razorpay, Cashfree, ...) rejects or fails a call.
 * Carries the HTTP status Fluxpay should return to its own caller.
 */
@Getter
public class PaymentGatewayException extends BusinessException {
    private final HttpStatus status;

    public PaymentGatewayException(String message, String errorCode, HttpStatus status) {
        super(message, errorCode);
        this.status = status;
    }
}

package com.fluxpay.payment.controller;

import com.fluxpay.payment.dto.RazorpayVerifyRequest;
import com.fluxpay.payment.dto.RazorpayVerifyResponse;
import com.fluxpay.payment.service.RazorpayPaymentService;
import com.fluxpay.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RazorpayController {

    private final RazorpayPaymentService razorpayPaymentService;

    /** Public: called from the hosted checkout page after the Razorpay modal succeeds. */
    @PostMapping("/payments/razorpay/verify")
    public ResponseEntity<ApiResponse<RazorpayVerifyResponse>> verifyPayment(@RequestBody RazorpayVerifyRequest request) {
        RazorpayVerifyResponse response = razorpayPaymentService.verifyCheckoutPayment(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment verified"));
    }

    /** Public: configure this URL in Razorpay Dashboard -> Webhooks. */
    @PostMapping("/webhooks/razorpay")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        try {
            if (!razorpayPaymentService.handleWebhook(rawBody, signature)) {
                log.warn("Rejected Razorpay webhook with invalid signature (is RAZORPAY_WEBHOOK_SECRET set?)");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
            }
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            // Non-2xx makes Razorpay retry the delivery
            log.error("Error processing Razorpay webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error");
        }
    }
}

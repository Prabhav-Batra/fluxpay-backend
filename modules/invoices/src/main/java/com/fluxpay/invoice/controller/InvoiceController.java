package com.fluxpay.invoice.controller;

import com.fluxpay.invoice.dto.GenerateInvoiceRequest;
import com.fluxpay.invoice.dto.InvoiceDto;
import com.fluxpay.invoice.service.InvoiceService;
import com.fluxpay.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping
    public ResponseEntity<ApiResponse<InvoiceDto>> generateInvoice(@Valid @RequestBody GenerateInvoiceRequest request) {
        InvoiceDto invoice = invoiceService.generateInvoice(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(invoice, "Invoice generated successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InvoiceDto>> getInvoice(@PathVariable UUID id) {
        InvoiceDto invoice = invoiceService.getInvoice(id);
        return ResponseEntity.ok(ApiResponse.success(invoice));
    }

    @GetMapping("/merchant/{merchantId}/customer/{email}")
    public ResponseEntity<ApiResponse<List<InvoiceDto>>> getCustomerInvoices(
            @PathVariable UUID merchantId, @PathVariable String email) {
        List<InvoiceDto> invoices = invoiceService.getCustomerInvoices(merchantId, email);
        return ResponseEntity.ok(ApiResponse.success(invoices));
    }
}

package com.fluxpay.invoice.service;

import com.fluxpay.invoice.dto.GenerateInvoiceRequest;
import com.fluxpay.invoice.dto.InvoiceDto;
import com.fluxpay.invoice.entity.Invoice;
import com.fluxpay.invoice.entity.InvoiceStatus;
import com.fluxpay.invoice.repository.InvoiceRepository;
import com.fluxpay.order.dto.OrderDto;
import com.fluxpay.order.service.OrderService;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final OrderService orderService;

    @Transactional
    public InvoiceDto generateInvoice(GenerateInvoiceRequest request) {
        OrderDto order = orderService.getOrder(request.getOrderId());

        Invoice invoice = Invoice.builder()
                .merchantId(order.getMerchantId())
                .customerEmail(order.getCustomerEmail())
                .orderId(order.getId())
                .invoiceNumber(generateInvoiceNumber())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .status("PAID".equals(order.getStatus().name()) ? InvoiceStatus.PAID : InvoiceStatus.OPEN)
                .build();

        invoice = invoiceRepository.save(invoice);
        
        // PDF Generation logic would be plugged in here later via an event or async queue.
        
        return mapToDto(invoice);
    }

    @Transactional(readOnly = true)
    public InvoiceDto getInvoice(UUID id) {
        return invoiceRepository.findById(id)
                .map(this::mapToDto)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id.toString()));
    }

    @Transactional(readOnly = true)
    public List<InvoiceDto> getCustomerInvoices(UUID merchantId, String customerEmail) {
        return invoiceRepository.findByMerchantIdAndCustomerEmail(merchantId, customerEmail).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InvoiceDto> getAllInvoices(UUID merchantId) {
        return invoiceRepository.findByMerchantId(merchantId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private String generateInvoiceNumber() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "INV-" + datePrefix + "-" + suffix;
    }

    private InvoiceDto mapToDto(Invoice invoice) {
        return InvoiceDto.builder()
                .id(invoice.getId())
                .merchantId(invoice.getMerchantId())
                .customerEmail(invoice.getCustomerEmail())
                .orderId(invoice.getOrderId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .totalAmount(invoice.getTotalAmount())
                .currency(invoice.getCurrency())
                .status(invoice.getStatus())
                .createdAt(invoice.getCreatedAt())
                .build();
    }
}

package com.fluxpay.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_line_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, name = "product_id")
    private UUID productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, name = "unit_price")
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal subtotal;
}

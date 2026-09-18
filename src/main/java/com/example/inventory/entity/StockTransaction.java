/**
 * 재고 이동 내역
 */
package com.example.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.math.BigDecimal;

import com.example.inventory.enums.TransactionType;
import com.example.inventory.enums.TransactionStatus;

@Entity
@Table(name = "stock_transactions")
@Getter
@NoArgsConstructor
public class StockTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = true)
    private BigDecimal unitPrice;

    @Column(name = "cost_price_snapshot", nullable = true)
    private BigDecimal costPriceSnapshot;

    @Column(name = "reason", nullable = true)
    private String reason;

    @Column(name = "reversal_of_id", nullable = true)
    private Long reversalOfId;

    @Column(name = "canceled_by", nullable = true)
    private Long canceledBy;

    @Column(name = "canceled_at", nullable = true)
    private LocalDateTime canceledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

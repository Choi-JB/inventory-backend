/**
 * 재고 이동 내역
 */
package com.example.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.example.inventory.enums.ConsumeType;

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

    // 이 거래를 등록한 사용자 (롤백 시 canceledBy와는 별개)
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // IN(입고) / OUT(출고) / CONSUME(자체소비) / ADJUSTMENT(조정)
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;

    // ACTIVE / CANCELED — 롤백되면 원본은 CANCELED로 마킹(삭제하지 않음)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    // 최소 단위 기준 수량. ADJUSTMENT는 부호 있는 값(±), 그 외는 항상 양수
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    // OUT일 때만 사용 (기본값 = Product.sellingPrice, 수기 override 가능)
    @Column(name = "unit_price", nullable = true)
    private BigDecimal unitPrice;

    // IN/OUT/CONSUME 시 거래 시점의 매입단가 스냅샷. 이후 매입가가 바뀌어도 과거 손익 계산엔 이 값 고정 사용
    @Column(name = "cost_price_snapshot", nullable = true)
    private BigDecimal costPriceSnapshot;

    // ADJUSTMENT는 필수(애플리케이션 레벨 검증), 그 외는 선택
    @Column(name = "reason", nullable = true)
    private String reason;

    // 롤백(상쇄) 트랜잭션인 경우, 상쇄 대상 원본 거래의 id — 순수 FK(자기참조), @ManyToOne 미사용
    @Column(name = "reversal_of_id", nullable = true)
    private Long reversalOfId;

    // 이 거래를 롤백 처리한 사용자 (ADMIN만 가능)
    @Column(name = "canceled_by", nullable = true)
    private Long canceledBy;

    @Column(name = "canceled_at", nullable = true)
    private LocalDateTime canceledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // CONSUME 타입(DISCARD, INTERNAL_USE, SAMPLE)
    @Enumerated(EnumType.STRING)
    @Column(name = "consume_type", nullable = true)
    private ConsumeType consumeType;


    public StockTransaction(Long productId, Long userId, Integer quantity, BigDecimal unitPrice, BigDecimal costPriceSnapshot, String reason, TransactionType type, TransactionStatus status, ConsumeType consumeType) {
        this.productId = productId;
        this.userId = userId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.costPriceSnapshot = costPriceSnapshot;
        this.reason = reason;
        this.type = type;
        this.status = status;
        this.consumeType = consumeType;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

}

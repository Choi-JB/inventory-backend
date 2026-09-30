/**
 * 제품 정보
 */
package com.example.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.math.RoundingMode;
import com.example.inventory.enums.Unit;
import com.example.inventory.exception.InsufficientStockException;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // 상품 고유 코드, 불변 (수정 API로도 변경 불가)
    @Column(name = "sku", nullable = false, unique = true)
    private String sku;

    // 말단(하위 카테고리 없는) 카테고리만 가능 — CategoryService.validateLeaf()로 검증
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    // EA / G / ML — 항상 최소 단위로 환산해서 저장 (kg→g, L→ml)
    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false)
    private Unit unit;

    // 가중평균 매입단가. 직접 수정 불가 — IN 트랜잭션 처리 시에만 자동 갱신
    @Column(name = "cost_price", nullable = false)
    private BigDecimal costPrice;

    // 기준 판매가. OUT 시 unitPrice 기본값으로 사용(override 가능)
    @Column(name = "selling_price", nullable = false)
    private BigDecimal sellingPrice;

    // 최소 단위 기준 재고 수량. 직접 수정 불가 — StockTransaction으로만 증감
    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    // 재고 부족 판단 기준 (currentStock <= minStockLevel)
    @Column(name = "min_stock_level", nullable = false)
    private Integer minStockLevel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    //생성자
    public Product(String name, String sku, Long categoryId, Unit unit, BigDecimal sellingPrice, Integer minStockLevel){
        this.name = name;
        this.sku = sku;
        this.categoryId = categoryId;
        this.unit = unit;
        this.sellingPrice = sellingPrice;
        this.minStockLevel = minStockLevel;
        this.createdAt = LocalDateTime.now();

        this.costPrice = BigDecimal.ZERO;
        this.currentStock = 0;
    }

    //생성 시 자동 설정
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // 상품 정보 수정
    public void update(String name, BigDecimal sellingPrice, Integer minStockLevel, Long categoryId) {
        // 힌트: Category.update()랑 똑같은 패턴 — 필드 4개 그대로 대입
        this.name = name;
        this.sellingPrice = sellingPrice;
        this.minStockLevel = minStockLevel;
        this.categoryId = categoryId;
    }

    // 재고 증가
    public void increaseStock(Integer quantity, BigDecimal unitPrice) {
        int oldStock = this.currentStock;
        this.currentStock += quantity;
        // 가중평균 매입단가 계산 (기존재고 × 기존단가) + (입고수량 × 입고단가)
        this.costPrice = (this.costPrice.multiply(BigDecimal.valueOf(oldStock)).add(unitPrice.multiply(BigDecimal.valueOf(quantity)))).divide(BigDecimal.valueOf(this.currentStock), 2, RoundingMode.HALF_UP);
    }

    // 재고 감소
    public void decreaseStock(Integer quantity) {
        if (this.currentStock < quantity) {
            throw new InsufficientStockException("재고가 부족합니다: " + this.id);
        }
        this.currentStock -= quantity;
    }

    // 재고 조정
    public void adjustStock(Integer actualQuantity) {
        if (actualQuantity < 0) {
            throw new InsufficientStockException("조정 수량은 0 이상이어야 합니다: " + this.id);
        }
        this.currentStock = actualQuantity;
    }

    // 재고 복구
    public void restoreStock(Integer quantity) {
        this.currentStock += quantity;
    }

    // 입고항목 롤백
    public void reverseIncreaseStock(Integer quantity, BigDecimal unitPrice) {
        if (this.currentStock < quantity) {
            throw new InsufficientStockException("재고가 부족합니다: " + this.id);
        }
        
        //재고가 0이 될경우 단가를 0원으로 설정
        if(this.currentStock - quantity == 0) {
            this.costPrice = BigDecimal.ZERO;
        } else {
            //복원된 단가 = (현재재고 × 현재단가 − 입고수량 × 입고단가) / (현재재고 − 입고수량)
        //(currentStock * costPrice - quantity * unitPrice) / (currentStock - quantity)
            BigDecimal newCostPrice = (this.costPrice.multiply(BigDecimal.valueOf(this.currentStock)).subtract(unitPrice.multiply(BigDecimal.valueOf(quantity)))).divide(BigDecimal.valueOf(this.currentStock - quantity), 2, RoundingMode.HALF_UP);
            this.costPrice = newCostPrice;
        }
        this.currentStock -= quantity;
    }
}

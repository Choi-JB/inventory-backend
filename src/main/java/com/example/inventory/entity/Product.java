/**
 * 제품 정보
 */
package com.example.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.inventory.enums.Unit;

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

    @Column(name = "sku", nullable = false, unique = true)
    private String sku;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false)
    private Unit unit;

    @Column(name = "cost_price", nullable = false)
    private BigDecimal costPrice;

    @Column(name = "selling_price", nullable = false)
    private BigDecimal sellingPrice;

    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

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

    public void update(String name, BigDecimal sellingPrice, Integer minStockLevel, Long categoryId) {
        // 힌트: Category.update()랑 똑같은 패턴 — 필드 4개 그대로 대입
        this.name = name;
        this.sellingPrice = sellingPrice;
        this.minStockLevel = minStockLevel;
        this.categoryId = categoryId;
    }
}

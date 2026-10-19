/**
 * ProductResponse - 상품 응답 DTO
 */
package com.example.inventory.dto.response;

import com.example.inventory.entity.Product;
import com.example.inventory.enums.Unit;
import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        String sku,
        Long categoryId,
        Unit unit,
        BigDecimal costPrice,
        BigDecimal sellingPrice,
        Integer currentStock,
        Integer minStockLevel
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getName(),
            product.getSku(),
            product.getCategoryId(),
            product.getUnit(),
            product.getCostPrice(),
            product.getSellingPrice(),
            product.getCurrentStock(),
            product.getMinStockLevel()
        );
    }
}
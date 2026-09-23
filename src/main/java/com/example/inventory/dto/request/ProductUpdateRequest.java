/**
 * ProductUpdateRequest - 상품 수정 요청 DTO
 */
package com.example.inventory.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;

public record ProductUpdateRequest(
    @NotBlank
    String name,

    @NotNull
    Long categoryId,

    @Positive
    @NotNull
    BigDecimal sellingPrice,

    Integer minStockLevel
) {
    
}

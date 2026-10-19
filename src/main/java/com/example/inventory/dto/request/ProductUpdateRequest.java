/**
 * ProductUpdateRequest - 상품 수정 요청 DTO
 */
package com.example.inventory.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;

public record ProductUpdateRequest(
    @NotBlank
    @Size(max = 200, message = "상품명은 200자 이하여야 합니다.")
    String name,

    @NotNull
    Long categoryId,

    @Positive
    @NotNull
    @Digits(integer = 10, fraction = 2, message = "가격은 정수 10자리, 소수 2자리까지 입력할 수 있습니다.")
    BigDecimal sellingPrice,

    Integer minStockLevel
) {
    
}

/**
 * StockConsumeRequest - 재고 소비 요청
 * DISCARD, INTERNAL_USE, SAMPLE
 * 
 */
package com.example.inventory.dto.request;

import com.example.inventory.enums.ConsumeType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StockConsumeRequest(
    @NotNull(message = "상품 ID는 필수 입력 항목입니다.")
    Long productId,
    @NotNull(message = "수량은 필수 입력 항목입니다.")
    @Positive(message = "수량은 양수여야 합니다.")
    Integer quantity,

    String reason,
    @NotNull(message = "소비 타입은 필수 입력 항목입니다.")
    ConsumeType consumeType
) {
    
}

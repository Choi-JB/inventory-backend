/**
 * StockAdjustmentRequest - 재고 조정 요청
 */
package com.example.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StockAdjustmentRequest (

    @NotNull(message = "상품 ID는 필수 입력 항목입니다.")
    Long productId,
    @NotNull(message = "수량은 필수 입력 항목입니다.")
    @PositiveOrZero(message = "수량은 0 이상이어야 합니다.")
    Integer actualQuantity,
    @NotBlank(message = "이유는 필수 입력 항목입니다.")
    String reason
) {
}

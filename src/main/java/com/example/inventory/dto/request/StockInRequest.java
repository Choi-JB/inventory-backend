/**
 * Stock in request
 * productId: string
 * quantity: number
 * unitPrice: number
 * reason: string
 */
package com.example.inventory.dto.request;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StockInRequest(
    @NotNull(message = "상품 ID는 필수 입력 항목입니다.")
    Long productId,
    @NotNull(message = "수량은 필수 입력 항목입니다.")
    @Positive(message = "수량은 양수여야 합니다.")
    Integer quantity,
    @NotNull(message = "가격은 필수 입력 항목입니다.")
    @Positive(message = "가격은 양수여야 합니다.")
    BigDecimal unitPrice,
    
    String reason
    ) {

}

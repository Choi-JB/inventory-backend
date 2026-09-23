/**
 * ProductCreateRequest - 상품 생성 요청 DTO
 */
package com.example.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.example.inventory.enums.Unit;
import java.math.BigDecimal;

// import 필요한 것들: jakarta.validation.constraints.* (NotBlank, NotNull, Positive 등)

public record ProductCreateRequest(
        // name: 필수 문자열
        // sku: 필수 문자열
        // categoryId: 필수 (null이면 안 됨 — NotBlank는 문자열 전용이라 여기엔 못 씁니다. 어떤 어노테이션이 적절할지 찾아보세요)
        // unit: 필수 — Product 엔티티에서 쓴 Unit enum 타입 그대로 써도 됩니다
        // sellingPrice: 필수, 0보다 커야 함 — 이것도 적절한 검증 어노테이션이 있습니다
        // minStockLevel: 선택값 (없으면 기본 0으로 처리할 예정 — Service에서 다룰 부분이라 여기선 그냥 필드만)
        @NotBlank
        String name,

        @NotBlank
        String sku,

        @NotNull
        Long categoryId,

        @NotNull
        Unit unit,

        @NotNull
        @Positive
        BigDecimal sellingPrice,
        
        Integer minStockLevel
) {
}
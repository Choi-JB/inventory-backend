/**
 * ProductCreateRequest - 상품 생성 요청 DTO
 */
package com.example.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;
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
        @Size(max = 200, message = "상품명은 200자 이하여야 합니다.")
        String name,

        @NotBlank
        @Size(max = 50, message = "상품 고유번호는 50자 이하여야 합니다.")
        String sku,

        @NotNull
        Long categoryId,

        @NotNull
        Unit unit,

        @NotNull
        @Positive
        @Digits(integer = 10, fraction = 2, message = "가격은 정수 10자리, 소수 2자리까지 입력할 수 있습니다.")
        BigDecimal sellingPrice,
        
        Integer minStockLevel
) {
}
/**
 * Category create request
 * name: string
 * description: string
 * parentId: string
 */
package com.example.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest (
    //null, 빈 문자열, 공백 막음 -> 실패 시 MethodArgumentNotValidException -> GlobalExceptionHandler -> 400 오류 반환
    @NotBlank(message = "카테고리명은 필수 입력 항목입니다.")
    @Size(max = 100, message = "카테고리명은 100자 이하여야 합니다.")
    String name,
    String description,
    Long parentId
) {
}

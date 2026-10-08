/**
 * ChatRequest - 채팅 요청
 */
package com.example.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
    @NotBlank(message = "메시지는 필수 입력 항목입니다.")
    @Size(max = 1000, message = "메시지는 최대 1000자 이내로 입력해주세요.")
    String message
) {
    
}

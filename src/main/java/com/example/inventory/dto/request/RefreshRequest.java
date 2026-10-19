package com.example.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
    @NotBlank(message = "리프레시 토큰은 필수 입력 항목입니다.")
    String refreshToken
) {
    
}

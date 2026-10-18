package com.example.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
    @NotBlank
    @Size(max = 50, message = "이메일은 50자 이하여야 합니다.")
    String email,
    @NotBlank
    @Size(min = 4, max = 20, message = "비밀번호는 4자 이상 20자 이하여야 합니다.")
    String password,
    @NotBlank
    @Size(min = 4, max = 20, message = "비밀번호 확인은 4자 이상 20자 이하여야 합니다.")
    String passwordConfirm
) {
} 

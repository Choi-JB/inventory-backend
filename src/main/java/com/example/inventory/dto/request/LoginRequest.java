package com.example.inventory.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인 요청 DTO
 * LoginRequest
 * @param email
 * @param password
 */
public record LoginRequest(
    @NotBlank
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    String email, 
    @NotBlank
    @Size(min = 4, max = 20, message = "비밀번호는 4자 이상 20자 이하여야 합니다.")
    String password) {
}

/**
 * ChatHistoryMessage - 채팅 이력 메시지
 */
package com.example.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatHistoryMessage(

    @NotBlank(message = "대화 이력의 역할(role)은 필수입니다.")
    @Pattern(regexp = "user|assistant", message = "대화 이력의 역할(role)은 user 또는 assistant만 가능합니다")
    String role,    // "user" 또는 "assistant"만 허용

    @NotBlank(message = "대화 이력의 내용(content)은 필수입니다.")
    @Size(max = 2000, message = "대화 이력의 내용(content)은 최대 2000자 이내로 입력해주세요.")
    String content    // 최대 2000자
) {}
/**
 * Category update request
 * name: string
 * description: string
 */
package com.example.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CategoryUpdateRequest(
        @NotBlank(message = "카테고리 이름은 필수입니다.")
        String name,

        String description
) {
}

/**
 * Category response dto - 카테고리 응답 DTO
 * id: number
 * name: string
 * description: string
 * parentId: number
 * createdAt: string
 */
package com.example.inventory.dto.response;

import com.example.inventory.entity.Category;
import java.time.LocalDateTime;

public record CategoryResponse(
        Long id,
        String name,
        String description,
        Long parentId,
        LocalDateTime createdAt
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getParentId(),
                category.getCreatedAt()
        );
    }
}
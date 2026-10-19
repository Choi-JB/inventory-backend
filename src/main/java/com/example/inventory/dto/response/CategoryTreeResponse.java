/**
 * Category tree response 카테고리를 트리 구조로 
 * id: string
 * name: string
 * description: string
 * children: CategoryTreeResponse[]
 */
package com.example.inventory.dto.response;

import com.example.inventory.entity.Category;

import java.util.List;

public record CategoryTreeResponse(
        Long id,
        String name,
        String description,
        List<CategoryTreeResponse> children
) {
    public static CategoryTreeResponse of(Category category, List<CategoryTreeResponse> children) {
        return new CategoryTreeResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                children
        );
    }
}

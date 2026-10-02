/**
 * Category controller
 * Get category tree, create, update, delete category
 */
package com.example.inventory.controller;

import com.example.inventory.dto.response.CategoryTreeResponse;
import com.example.inventory.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import com.example.inventory.dto.request.CategoryCreateRequest;
import com.example.inventory.dto.request.CategoryUpdateRequest;
import com.example.inventory.dto.response.CategoryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;


import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "카테고리", description = "카테고리 트리 조회 및 관리 (등록/수정/삭제는 ADMIN 전용)")
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    //카테고리 조회
    @Operation(summary = "카테고리 트리 조회", description = "전체 카테고리를 children이 중첩된 트리 구조로 반환합니다.")
    @GetMapping
    public ResponseEntity<List<CategoryTreeResponse>> getCategoryTree() {
        return ResponseEntity.ok(categoryService.getTree());
    }

    //카테고리 생성
    //POST /api/categories - ADMIN만 가능, 성공  시 201 Created + 생성된 카테고리 반환
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "카테고리 생성", description = "ADMIN 전용. parentId가 없으면 최상위 카테고리, 존재하지 않는 parentId면 404.")
    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryCreateRequest request){
         // 힌트: categoryService.create(request) 호출 결과를 어떤 HTTP status로 감쌀지 생각해보세요.
        CategoryResponse category = categoryService.create(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(category);
    }
    
    // PUT /api/categories/{id} — ADMIN만 가능, 200 OK + 수정된 카테고리
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "카테고리 수정", description = "ADMIN 전용. name, description만 수정 가능하며 부모 카테고리 이동은 지원하지 않습니다.")
    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryUpdateRequest request) {
        // 힌트: id와 request를 그대로 service.update(...)에 넘기면 됩니다.
        CategoryResponse category = categoryService.update(id, request);

        return ResponseEntity.status(HttpStatus.OK).body(category);
    }

    // DELETE /api/categories/{id} — ADMIN만 가능, 204 No Content
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "카테고리 삭제", description = "ADMIN 전용. 하위 카테고리나 소속 상품이 있으면 409(DELETE_CONFLICT).")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        // 힌트: 삭제는 반환할 데이터가 없습니다. ResponseEntity.noContent()... 를 찾아보세요.
        categoryService.delete(id);
        return ResponseEntity.noContent().build ();
    }


}

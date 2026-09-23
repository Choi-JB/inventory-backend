/**
 * Category controller
 * Get category tree, create, update, delete category
 */
package com.example.inventory.controller;

import com.example.inventory.dto.response.CategoryTreeResponse;
import com.example.inventory.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.inventory.entity.Category;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import com.example.inventory.dto.request.CategoryCreateRequest;
import com.example.inventory.dto.request.CategoryUpdateRequest;
import com.example.inventory.dto.response.CategoryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;


import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    //카테고리 조회
    @GetMapping
    public ResponseEntity<List<CategoryTreeResponse>> getCategoryTree() {
        return ResponseEntity.ok(categoryService.getTree());
    }

    //카테고리 생성
    //POST /api/categories - ADMIN만 가능, 성공  시 201 Created + 생성된 카테고리 반환
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryCreateRequest request){
         // 힌트: categoryService.create(request) 호출 결과를 어떤 HTTP status로 감쌀지 생각해보세요.
        CategoryResponse category = categoryService.create(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(category);
    }
    
    // PUT /api/categories/{id} — ADMIN만 가능, 200 OK + 수정된 카테고리
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryUpdateRequest request) {
        // 힌트: id와 request를 그대로 service.update(...)에 넘기면 됩니다.
        CategoryResponse category = categoryService.update(id, request);

        return ResponseEntity.status(HttpStatus.OK).body(category);
    }

    // DELETE /api/categories/{id} — ADMIN만 가능, 204 No Content
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        // 힌트: 삭제는 반환할 데이터가 없습니다. ResponseEntity.noContent()... 를 찾아보세요.
        categoryService.delete(id);
        return ResponseEntity.noContent().build ();
    }
}

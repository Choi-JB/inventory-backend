/**
 * Product controller
 * Get product list, get product by id, create product, update product, delete product
 */
package com.example.inventory.controller;

import com.example.inventory.dto.response.ProductResponse;
import com.example.inventory.dto.response.PageResponse;
import com.example.inventory.service.ProductService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import com.example.inventory.dto.request.ProductCreateRequest;
import com.example.inventory.dto.request.ProductUpdateRequest;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;

@Tag(name = "상품", description = "상품 조회/검색 및 관리 (등록/수정/삭제는 ADMIN 전용)")
@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // GET /api/products — 인증만 필요, 목록 조회
    @Operation(summary = "상품 목록 검색", description = "keyword(이름/SKU 부분일치), categoryId(하위 카테고리 포함), lowStockOnly 조건을 선택적으로 조합하고 페이징합니다.")
    @GetMapping
    public ResponseEntity<PageResponse<ProductResponse>> getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean lowStockOnly,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(productService.search(keyword, categoryId, lowStockOnly, pageable)));
    }

    // GET /api/products/{id} — 인증만 필요, 단건 상세
    @Operation(summary = "상품 단건 조회", description = "존재하지 않는 id면 404.")
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    // POST /api/products — ADMIN만, 201 Created
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "상품 등록", description = "ADMIN 전용. costPrice와 currentStock은 0으로 초기화됩니다(입고로만 증가).")
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        ProductResponse product = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    // PUT /api/products/{id} — ADMIN만, 200 OK
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "상품 수정", description = "ADMIN 전용. name, sellingPrice, minStockLevel, categoryId만 수정 가능(sku/단가/재고는 불가).")
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        ProductResponse product = productService.update(id, request);
        return ResponseEntity.status(HttpStatus.OK).body(product);
    }

    // DELETE /api/products/{id} — ADMIN만, 204 No Content
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "상품 삭제", description = "ADMIN 전용. 연결된 재고 거래 이력이 있으면 409(DELETE_CONFLICT).")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

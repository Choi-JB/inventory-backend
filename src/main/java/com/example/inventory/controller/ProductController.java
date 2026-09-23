/**
 * Product controller
 * Get product list, get product by id, create product, update product, delete product
 */
package com.example.inventory.controller;

import com.example.inventory.dto.response.ProductResponse;
import com.example.inventory.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import com.example.inventory.dto.request.ProductCreateRequest;
import com.example.inventory.dto.request.ProductUpdateRequest;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // GET /api/products — 인증만 필요, 목록 조회
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean lowStockOnly,
            Pageable pageable) {
        return ResponseEntity.ok(productService.search(keyword, categoryId, lowStockOnly, pageable));
    }

    // GET /api/products/{id} — 인증만 필요, 단건 상세
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    // POST /api/products — ADMIN만, 201 Created
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        ProductResponse product = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    // PUT /api/products/{id} — ADMIN만, 200 OK
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        ProductResponse product = productService.update(id, request);
        return ResponseEntity.status(HttpStatus.OK).body(product);
    }

    // DELETE /api/products/{id} — ADMIN만, 204 No Content
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

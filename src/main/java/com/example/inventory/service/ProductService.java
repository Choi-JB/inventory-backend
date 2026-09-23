/**
 * ProductService.java
 * 상품 서비스
 * 상품 검색 필터 기능 구현
 * 상품 검색 필터 기능 구현
 */
package com.example.inventory.service;

import org.springframework.stereotype.Service;
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.service.CategoryService;
import com.example.inventory.repository.StockTransactionRepository;
import org.springframework.data.jpa.domain.Specification;
import com.example.inventory.entity.Product;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.inventory.dto.response.ProductResponse;
import org.springframework.transaction.annotation.Transactional;
import com.example.inventory.dto.request.ProductCreateRequest;
import com.example.inventory.dto.request.ProductUpdateRequest;
import com.example.inventory.exception.NotFoundException;
import com.example.inventory.exception.DeleteConflictException;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    private final CategoryService categoryService;

    private final StockTransactionRepository stockTransactionRepository;

    public ProductService(ProductRepository productRepository, CategoryService categoryService, StockTransactionRepository stockTransactionRepository) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
        this.stockTransactionRepository = stockTransactionRepository;
    }
    
    //상품 이름 또는 SKU에 포함된 키워드 검색
    private Specification<Product> keywordContains(String keyword) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
            criteriaBuilder.like(root.get("name"), "%" + keyword + "%"),
            criteriaBuilder.like(root.get("sku"), "%" + keyword + "%")
        );
    }
    
    //카테고리 ID 목록에 포함된 상품 검색
    private Specification<Product> categoryIdIn(List<Long> categoryIds) {
        // 힌트: root.get("categoryId")가 categoryIds 리스트 안에 있는지 -> cb.in(...) 사용
        return (root, query, cb) -> root.get("categoryId").in(categoryIds);
    }
    
    //재고가 최소 재고 수준 이하인 상품 검색 : (재고 부족 상태인 상품 검색)
    private Specification<Product> isLowStock() {
        // 힌트: currentStock <= minStockLevel 비교인데, 둘 다 "값"이 아니라 "같은 테이블의 다른 컬럼"입니다.
        // cb.lessThanOrEqualTo(A, B) 형태를 쓰되, A/B 자리에 상수 대신 root.get("currentStock"), root.get("minStockLevel")을 넣으면 됩니다.
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("currentStock"), root.get("minStockLevel"));
    }

    //상품 검색 필터 기능 구현
    public Page<ProductResponse> search(String keyword, Long categoryId, Boolean lowStockOnly, Pageable pageable) {
        Specification<Product> spec = (root, query, cb) -> cb.conjunction();
    
        if (keyword != null && !keyword.isBlank()) {
            spec = spec.and(keywordContains(keyword));
        }
    
        if (categoryId != null) {
            List<Long> categoryIds = categoryService.getDescendantCategoryIds(categoryId);
            spec = spec.and(categoryIdIn(categoryIds));
        }
    
        if (Boolean.TRUE.equals(lowStockOnly)) {
            spec = spec.and(isLowStock());
        }
    
        Page<Product> products = productRepository.findAll(spec, pageable);
        return products.map(ProductResponse::from);
    }


    //상품 조회
    @Transactional
    public ProductResponse getById(Long id) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다: " + id));
        return ProductResponse.from(product);
    }

    //상품 생성
    @Transactional
    public ProductResponse create(ProductCreateRequest request){
        //말단 카테고리 검증
        categoryService.validateLeaf(request.categoryId());

        Product product = new Product(request.name(), request.sku(), request.categoryId(), request.unit(), request.sellingPrice(), request.minStockLevel());
        return ProductResponse.from(productRepository.save(product));
    }

    //상품 수정
    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request){
        Product product = productRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다: " + id));

        //말단 카테고리 검증
        categoryService.validateLeaf(request.categoryId());

        product.update(request.name(), request.sellingPrice(), request.minStockLevel(), request.categoryId());
        return ProductResponse.from(product);
    }

    //상품 삭제
    @Transactional
    public void delete(Long id){
        Product product = productRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다: " + id));
        
        //연결된 거래이력 존재 시 삭제 거부
        if (stockTransactionRepository.existsByProductId(id)) {
            throw new DeleteConflictException("연결된 거래이력이 존재하여 삭제할 수 없습니다: " + id);
        }

        productRepository.delete(product);
    }
}

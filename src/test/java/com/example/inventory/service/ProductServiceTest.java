package com.example.inventory.service;

import com.example.inventory.dto.request.ProductCreateRequest;
import com.example.inventory.dto.request.ProductUpdateRequest;
import com.example.inventory.dto.response.ProductResponse;
import com.example.inventory.entity.Product;
import com.example.inventory.enums.Unit;
import com.example.inventory.exception.ConflictException;
import com.example.inventory.exception.DeleteConflictException;
import com.example.inventory.exception.NotFoundException;
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.repository.StockTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private StockTransactionRepository stockTransactionRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void getById_있으면응답을반환한다() {
        Product product = product(1L, "아메리카노", "SKU-1");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ProductResponse response = productService.getById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("아메리카노");
    }

    @Test
    void getById_없으면NotFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById(1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("상품을 찾을 수 없습니다");
    }

    @Test
    void create_성공하면저장한다() {
        ProductCreateRequest request = new ProductCreateRequest(
                "아메리카노", "SKU-1", 10L, Unit.EA, new BigDecimal("3000"), 5);
        doNothing().when(categoryService).validateExists(10L);
        when(productRepository.existsBySku("SKU-1")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        ProductResponse response = productService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.sku()).isEqualTo("SKU-1");
        verify(categoryService).validateExists(10L);
    }

    @Test
    void create_sku중복이면Conflict() {
        ProductCreateRequest request = new ProductCreateRequest(
                "아메리카노", "SKU-1", 10L, Unit.EA, new BigDecimal("3000"), 5);
        doNothing().when(categoryService).validateExists(10L);
        when(productRepository.existsBySku("SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("이미 존재");
        verify(productRepository, never()).save(any());
    }

    @Test
    void update_상품정보를수정한다() {
        Product product = product(1L, "아메리카노", "SKU-1");
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        doNothing().when(categoryService).validateExists(20L);

        ProductResponse response = productService.update(
                1L,
                new ProductUpdateRequest("라떼", 20L, new BigDecimal("4000"), 3));

        assertThat(response.name()).isEqualTo("라떼");
        assertThat(response.sellingPrice()).isEqualByComparingTo("4000");
        assertThat(response.categoryId()).isEqualTo(20L);
        assertThat(response.minStockLevel()).isEqualTo(3);
    }

    @Test
    void delete_거래이력이있으면충돌() {
        Product product = product(1L, "아메리카노", "SKU-1");
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(stockTransactionRepository.existsByProductId(1L)).thenReturn(true);

        assertThatThrownBy(() -> productService.delete(1L))
                .isInstanceOf(DeleteConflictException.class)
                .hasMessageContaining("거래이력");
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void delete_거래이력이없으면삭제한다() {
        Product product = product(1L, "아메리카노", "SKU-1");
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(stockTransactionRepository.existsByProductId(1L)).thenReturn(false);

        productService.delete(1L);

        verify(productRepository).delete(product);
    }

    @Test
    void search_필터결과를페이지로반환한다() {
        Product product = product(1L, "아메리카노", "SKU-1");
        PageRequest pageable = PageRequest.of(0, 10);
        when(categoryService.getDescendantCategoryIds(10L)).thenReturn(List.of(10L, 11L));
        when(productRepository.findAll(ArgumentMatchers.<Specification<Product>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(product), pageable, 1));

        Page<ProductResponse> result = productService.search("아메", 10L, true, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("아메리카노");
        verify(categoryService).getDescendantCategoryIds(10L);
    }

    private static Product product(Long id, String name, String sku) {
        Product product = new Product(name, sku, 10L, Unit.EA, new BigDecimal("3000"), 5);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}

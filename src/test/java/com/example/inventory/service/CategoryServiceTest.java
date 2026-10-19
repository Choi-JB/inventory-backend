package com.example.inventory.service;

import com.example.inventory.dto.request.CategoryCreateRequest;
import com.example.inventory.dto.request.CategoryUpdateRequest;
import com.example.inventory.dto.response.CategoryResponse;
import com.example.inventory.dto.response.CategoryTreeResponse;
import com.example.inventory.entity.Category;
import com.example.inventory.exception.DeleteConflictException;
import com.example.inventory.exception.NotFoundException;
import com.example.inventory.repository.CategoryRepository;
import com.example.inventory.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void getTree_부모자식구조를트리로조립한다() {
        Category root = category(1L, "음료", null);
        Category child = category(2L, "커피", 1L);
        when(categoryRepository.findAll()).thenReturn(List.of(root, child));

        List<CategoryTreeResponse> tree = categoryService.getTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).id()).isEqualTo(1L);
        assertThat(tree.get(0).children()).hasSize(1);
        assertThat(tree.get(0).children().get(0).id()).isEqualTo(2L);
    }

    @Test
    void create_부모없이생성한다() {
        CategoryCreateRequest request = new CategoryCreateRequest("음료", "설명", null);
        Category saved = category(1L, "음료", null);
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);

        CategoryResponse response = categoryService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("음료");
        verify(categoryRepository, never()).existsById(any());
    }

    @Test
    void create_부모카테고리가없으면NotFound() {
        CategoryCreateRequest request = new CategoryCreateRequest("커피", null, 99L);
        when(categoryRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> categoryService.create(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("부모 카테고리");
    }

    @Test
    void update_이름을수정한다() {
        Category category = category(1L, "음료", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        CategoryResponse response = categoryService.update(1L, new CategoryUpdateRequest("음료수", "수정"));

        assertThat(response.name()).isEqualTo("음료수");
        assertThat(response.description()).isEqualTo("수정");
    }

    @Test
    void update_없으면NotFound() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(1L, new CategoryUpdateRequest("음료", null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_하위카테고리가있으면충돌() {
        Category category = category(1L, "음료", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParentId(1L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(1L))
                .isInstanceOf(DeleteConflictException.class)
                .hasMessageContaining("하위 카테고리");
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void delete_소속상품이있으면충돌() {
        Category category = category(1L, "음료", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParentId(1L)).thenReturn(false);
        when(productRepository.existsByCategoryId(1L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(1L))
                .isInstanceOf(DeleteConflictException.class)
                .hasMessageContaining("소속된 상품");
    }

    @Test
    void delete_성공하면repository에서삭제한다() {
        Category category = category(1L, "음료", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParentId(1L)).thenReturn(false);
        when(productRepository.existsByCategoryId(1L)).thenReturn(false);

        categoryService.delete(1L);

        verify(categoryRepository).delete(category);
    }

    @Test
    void validateExists_없으면NotFound() {
        when(categoryRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> categoryService.validateExists(1L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getDescendantCategoryIds_자신과하위id를모두반환한다() {
        Category root = category(1L, "음료", null);
        Category child = category(2L, "커피", 1L);
        Category grandChild = category(3L, "아메리카노", 2L);
        when(categoryRepository.findAll()).thenReturn(List.of(root, child, grandChild));

        List<Long> ids = categoryService.getDescendantCategoryIds(1L);

        assertThat(ids).containsExactly(1L, 2L, 3L);
    }

    private static Category category(Long id, String name, Long parentId) {
        Category category = new Category(name, null, parentId);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}

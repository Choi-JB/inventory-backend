/**
 * 트리 조회, 생성/수정/삭제 + 검증 
 * 트리 조립 + CRUD 검증
 */
package com.example.inventory.service;

import com.example.inventory.dto.request.CategoryCreateRequest;
import com.example.inventory.dto.request.CategoryUpdateRequest;
import com.example.inventory.dto.response.CategoryTreeResponse;
import com.example.inventory.dto.response.CategoryResponse;
import com.example.inventory.entity.Category;
import com.example.inventory.exception.DeleteConflictException;
import com.example.inventory.exception.NotFoundException;
import com.example.inventory.repository.CategoryRepository;
import com.example.inventory.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    //트리 조회
    public List<CategoryTreeResponse> getTree() {
        //1. 전체를 한번에 다 가져옴
        List<Category> allCategories = categoryRepository.findAll();

        //2. 부모 카테고리에 따라 그룹화
        //부모 ID별로 자식들을 묶은 Map 생성
        Map<Long, List<Category>> childrenByParentId = allCategories.stream()
                .filter(category -> category.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));

        //3. 부모 카테고리를 기준으로 트리 구조 생성
        return allCategories.stream()
                .filter(category -> category.getParentId() == null)
                .map(root -> buildTree(root, childrenByParentId))
                .toList();
    }

    //트리 조립
    private CategoryTreeResponse buildTree(Category category, Map<Long, List<Category>> childrenByParentId) {
        List<CategoryTreeResponse> children = childrenByParentId
                .getOrDefault(category.getId(), List.of())
                .stream()
                .map(child -> buildTree(child, childrenByParentId))
                .toList();

        return CategoryTreeResponse.of(category, children);
    }

    //생성
    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        if (request.parentId() != null && !categoryRepository.existsById(request.parentId())) {
            throw new NotFoundException("부모 카테고리를 찾을 수 없습니다: " + request.parentId());
        }

        Category category = new Category(request.name(), request.description(), request.parentId());
        //return CategoryResponse.from(categoryRepository.save(category));
        //findById로 가져온 영속 상태 엔티티 이기때문에 save 하지 않아도 알아서 갱신됨
        return CategoryResponse.from(category);
    }

    //수정
    @Transactional
    public CategoryResponse update(Long id, CategoryUpdateRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("카테고리를 찾을 수 없습니다: " + id));

        category.update(request.name(), request.description());
        //return CategoryResponse.from(categoryRepository.save(category));
        //findById로 가져온 영속 상태 엔티티 이기때문에 save 하지 않아도 알아서 갱신됨
        return CategoryResponse.from(category);
    }

    //삭제
    @Transactional
    public void delete(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("카테고리를 찾을 수 없습니다: " + id));

        if (categoryRepository.existsByParentId(id)) {
            throw new DeleteConflictException("하위 카테고리가 존재하여 삭제할 수 없습니다.");
        }
        if (productRepository.existsByCategoryId(id)) {
            throw new DeleteConflictException("소속된 상품이 존재하여 삭제할 수 없습니다.");
        }

        categoryRepository.delete(category);
    }
}

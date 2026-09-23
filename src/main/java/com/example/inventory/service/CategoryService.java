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
import com.example.inventory.exception.ValidationException;
import com.example.inventory.repository.CategoryRepository;
import com.example.inventory.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList; 
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
        return CategoryResponse.from(categoryRepository.save(category));
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

    // 상품 등록 시 categoryId가 말단(하위 카테고리 없음)인지 검증
    public void validateLeaf(Long categoryId) {
        // 힌트 1: 카테고리 자체가 존재하는지부터 확인 (없으면 NotFoundException)
            Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("카테고리를 찾을 수 없습니다: " + categoryId));

        // 힌트 2: categoryRepository.existsByParentId(categoryId)로 하위 존재 여부 확인
        //         하위가 있으면 → ValidationException("말단 카테고리가 아닙니다" 같은 메시지)
        if (categoryRepository.existsByParentId(categoryId)) {
            throw new ValidationException("말단 카테고리가 아닙니다: " + categoryId);
        }
    }

    // categoryId 자기 자신 + 모든 하위 카테고리 id 목록 (재귀) — 상품 검색 필터용
    public List<Long> getDescendantCategoryIds(Long categoryId) {
        //1. 전체 카테고리 목록을 가져온다
        List<Category> allCategories = categoryRepository.findAll();

        //2. 부모 카테고리에 따라 그룹화
        Map<Long, List<Category>> childrenByParentId = allCategories.stream()
                    .filter(category -> category.getParentId() != null)
                    .collect(Collectors.groupingBy(Category::getParentId));

        //3. 자기 자신 id + 모든 자식들의 id를 하나의 List<Long>에 모아서 반환
        List<Long> result = new ArrayList<>();
        collectDescendants(categoryId, childrenByParentId, result);
        return result;
    }

    
    private void collectDescendants(Long categoryId, Map<Long, List<Category>> childrenByParentId, List<Long> result) {
        //1. result에 지금 categoryId를 추가한다 
        result.add(categoryId);

        //2. childrenByParentId에서 이 categoryId의 자식 목록을 꺼낸다 (getOrDefault 사용, 없으면 빈 리스트)
        List<Category> children = childrenByParentId.getOrDefault(categoryId, List.of());

        // 3. 꺼낸 자식들 각각에 대해 이 메서드(collectDescendantIds)를 자기 자신 재귀 호출한다 (자식의 id로)
        children.forEach(child -> collectDescendants(child.getId(), childrenByParentId, result));
    }
    

}

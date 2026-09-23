package com.example.inventory.repository;

import com.example.inventory.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
    
public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsByParentId(Long parentId);    //부모 카테고리 존재 여부 확인
}

package com.example.inventory.repository;

import com.example.inventory.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
    
public interface CategoryRepository extends JpaRepository<Category, Long> {
    
}

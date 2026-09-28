/**
 * 카테고리
 */

package com.example.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "description", nullable = true)
    private String description;

    // 같은 categories 테이블의 다른 행을 가리키는 FK. null이면 최상위(대분류)
    // @ManyToOne 대신 순수 FK로 매핑 — 트리 조립은 CategoryService에서 애플리케이션 레벨로 처리
    @Column(name = "parent_id", nullable = true)
    private Long parentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Category(String name, String description, Long parentId) {
        this.name = name;
        this.description = description;
        this.parentId = parentId;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }
}

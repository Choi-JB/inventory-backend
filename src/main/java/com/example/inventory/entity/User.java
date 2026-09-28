/**
 * 사용자 정보
 */
package com.example.inventory.entity;

import com.example.inventory.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;


@Entity
@Table (name = "users")
@Getter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 구글 OAuth sub 값 (구글이 발급하는 사용자 고유 ID)
    @Column(name = "google_id", nullable = false, unique = true)
    private String googleId;

    @Column(nullable = false, unique = true)
    private String email;

    // ADMIN / STAFF, 기본값 STAFF (신규 가입 시 자동 부여, 승격은 DB 직접 처리)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public User(String googleId, String email, Role role) {
        this.googleId = googleId;
        this.email = email;
        this.role = role;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

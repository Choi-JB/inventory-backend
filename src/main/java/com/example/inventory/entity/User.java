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
    @Column(name = "google_id", unique = true)
    private String googleId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    // ADMIN / STAFF, 기본값 STAFF (신규 가입 시 자동 부여, 승격은 DB 직접 처리)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "name")
    private String name;

    //구글 로그인 사용자 생성
    public static User google(String googleId, String email, Role role) {
        User user = new User();
        user.googleId = googleId;
        user.email = email;
        user.role = role;
        return user;
    }

    //일반 로그인 사용자 생성
    public static User local(String email, String passwordHash, Role role) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.role = role;
        return user;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    //이름 자동 생성
    public void assignDefaultUserName() {
        this.name = this.role.name() + "-" + this.id; // STAFF-12
    }
}
    

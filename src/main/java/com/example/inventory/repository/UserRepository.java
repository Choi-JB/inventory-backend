package com.example.inventory.repository;

import com.example.inventory.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

// JpaRepository<User, Long>만 상속하면 save, findById, findAll 등이 기본 제공
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByGoogleId(String googleId);
}

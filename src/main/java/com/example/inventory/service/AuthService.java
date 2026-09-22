/**
 * Google OAuth2 로그인 처리
 * UserRepository로 google_id 기준 조회 → 없으면 새로 생성(role 기본값 STAFF)
 * JwtProvider.generateToken(user)로 JWT 발급
 * { accessToken, user } 형태로 JSON 응답 작성
 */
package com.example.inventory.service;

import com.example.inventory.dto.response.LoginResponse;
import com.example.inventory.entity.User;
import com.example.inventory.enums.Role;
import com.example.inventory.repository.UserRepository;
import com.example.inventory.security.JwtProvider;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    public AuthService(UserRepository userRepository, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
    }

    public LoginResponse loginWithGoogle(String googleId, String email) {
        User user = userRepository.findByGoogleId(googleId)
                .orElseGet(() -> userRepository.save(new User(googleId, email, Role.STAFF)));

        String accessToken = jwtProvider.generateToken(user);

        return new LoginResponse(
                accessToken,
                new LoginResponse.UserInfo(user.getId(), user.getEmail(), user.getRole().name())
        );
    }
}
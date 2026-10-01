package com.example.inventory.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.example.inventory.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import com.example.inventory.security.SecurityUtils;
import com.example.inventory.dto.response.LoginResponse;
import com.example.inventory.entity.User;
import com.example.inventory.exception.NotFoundException;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    //GET /api/auth/me: SecurityContextHolder
    //id, email, role 반환
    @GetMapping("/me")
    public ResponseEntity<LoginResponse.UserInfo> getMe() {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));

        return ResponseEntity.status(HttpStatus.OK).body(new LoginResponse.UserInfo(user.getId(), user.getEmail(), user.getRole().name()));
    }

    //POST /api/auth/logout
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("accessToken", "")
            .path("/")
            .secure(false) // 개발 환경에서는 보안 쿠키 사용 X
            .httpOnly(true)
            .sameSite("Lax") // 개발 환경에서는 동일 사이트 쿠키 사용 X
            .maxAge(0) // 쿠키 만료 시간 0
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok().build();
    }
}

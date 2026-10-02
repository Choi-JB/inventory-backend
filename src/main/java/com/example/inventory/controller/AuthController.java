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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "인증", description = "로그인 사용자 확인, 로그아웃 (구글 로그인은 /oauth2/authorization/google 로 브라우저 이동)")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    //GET /api/auth/me: SecurityContextHolder
    //id, email, role 반환
    @Operation(summary = "내 정보 조회", description = "쿠키의 JWT로 인증된 현재 사용자의 id, email, role을 반환합니다. 로그인하지 않았으면 401.")
    @GetMapping("/me")
    public ResponseEntity<LoginResponse.UserInfo> getMe() {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));

        return ResponseEntity.status(HttpStatus.OK).body(new LoginResponse.UserInfo(user.getId(), user.getEmail(), user.getRole().name()));
    }

    //POST /api/auth/logout
    @Operation(summary = "로그아웃", description = "accessToken 쿠키를 만료시켜 브라우저가 삭제하도록 합니다.")
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

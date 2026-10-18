package com.example.inventory.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;

import com.example.inventory.dto.request.LoginRequest;
import com.example.inventory.dto.request.SignUpRequest;
import com.example.inventory.dto.request.RefreshRequest;
import com.example.inventory.repository.UserRepository;
import com.example.inventory.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import com.example.inventory.security.SecurityUtils;
import com.example.inventory.dto.response.LoginResponse;
import com.example.inventory.entity.User;
import com.example.inventory.exception.NotFoundException;
import com.example.inventory.exception.InvalidTokenException;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import jakarta.servlet.http.HttpServletRequest;
import com.example.inventory.security.AuthCookieUtils;
import org.springframework.security.core.context.SecurityContextHolder;

@Tag(name = "인증", description = "로그인 사용자 확인, 로그아웃 (구글 로그인은 /oauth2/authorization/google 로 브라우저 이동)")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    // JWT 만료시간
    @Value("${jwt.expiration}")
    private long expirationMs;
    // 리프레시 토큰 만료시간
    @Value("${jwt.refreshExpirationMs}")
    private long refreshExpirationMs;

    private final UserRepository userRepository;
    private final AuthService authService;

    public AuthController(UserRepository userRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    // GET /api/auth/me: SecurityContextHolder
    // id, email, role 반환
    @Operation(summary = "내 정보 조회", description = "쿠키의 JWT로 인증된 현재 사용자의 id, email, role을 반환합니다. 로그인하지 않았으면 401.")
    @GetMapping("/me")
    public ResponseEntity<LoginResponse.UserInfo> getMe() {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));

        return ResponseEntity.status(HttpStatus.OK)
                .body(new LoginResponse.UserInfo(user.getId(), user.getEmail(), user.getRole().name()));
    }

    // POST /api/auth/login
    @Operation(summary = "로그인", description = "일반 로그인 후 토큰을 반환합니다.")
    @PostMapping("/login")
    public ResponseEntity<Void> login(
            @RequestBody @Valid LoginRequest loginRequest
        ) {
        
            LoginResponse loginResponse = authService.login(loginRequest);

            return ResponseEntity.status(HttpStatus.OK)
                    .header(HttpHeaders.SET_COOKIE, AuthCookieUtils.accessTokenCookie(loginResponse.accessToken(), Duration.ofMillis(expirationMs)).toString())
                    .header(HttpHeaders.SET_COOKIE, AuthCookieUtils.refreshTokenCookie(loginResponse.refreshToken(), Duration.ofMillis(refreshExpirationMs)).toString())
                    .build();
    }

    // POST /api/auth/logout
    @Operation(summary = "로그아웃", description = "accessToken 쿠키를 만료시켜 브라우저가 삭제하도록 합니다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {

        response.addHeader(HttpHeaders.SET_COOKIE, AuthCookieUtils.clearAccessTokenCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, AuthCookieUtils.clearRefreshTokenCookie().toString());

        var session = request.getSession(false);
        if(session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    // POST /api/auth/signup
    @Operation(summary = "회원가입", description = "이메일과 비밀번호로 회원가입합니다.")
    @PostMapping("/signup")
    public ResponseEntity<Void> signUp(
            @RequestBody @Valid SignUpRequest signUpRequest) {
        authService.signUp(signUpRequest);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    // POST /api/auth/refresh
    @Operation(summary = "토큰 갱신", description = "refreshToken을 사용하여 accessToken을 갱신합니다.")
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest request) {
            String refreshToken = AuthCookieUtils.getCookieValue(request, "refreshToken");

            if (refreshToken == null || refreshToken.isBlank()) {
                throw new InvalidTokenException("유효하지 않은 토큰입니다.");
            }
       
            LoginResponse loginResponse = authService.refresh(new RefreshRequest(refreshToken));

            return ResponseEntity.status(HttpStatus.OK)
                    .header(HttpHeaders.SET_COOKIE, AuthCookieUtils.accessTokenCookie(loginResponse.accessToken(), Duration.ofMillis(expirationMs)).toString())
                    .header(HttpHeaders.SET_COOKIE, AuthCookieUtils.refreshTokenCookie(loginResponse.refreshToken(), Duration.ofMillis(refreshExpirationMs)).toString())
                    .build();
    }
}

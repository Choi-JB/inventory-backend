/**
 * OAuth2 로그인 성공 후 넘어온 사용자 정보 추출 google_id, email
 * UserRepository로 google_id 기준 조회 → 없으면 새로 생성(role 기본값 STAFF)
 * JwtProvider.generateToken(user)로 JWT 발급
 * { accessToken, user } 형태로 JSON 응답 작성 (일반 리다이렉트가 아니라 직접 응답 바디를 씀)
 */
package com.example.inventory.security;

import com.example.inventory.dto.response.LoginResponse;
import com.example.inventory.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Duration;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler{

    private final AuthService authService;
    private final AuthCookieUtils authCookieUtils;

    //JWT 만료시간
    @Value("${jwt.expiration}")
    private long expirationMs;
    //리프레시 토큰 만료시간
    @Value("${jwt.refreshExpirationMs}")
    private long refreshExpirationMs;

    @Value("${frontend.url}")
    private String frontendUrl;

    public OAuth2LoginSuccessHandler(
            AuthService authService,
            AuthCookieUtils authCookieUtils
    ) {
        this.authService = authService;
        this.authCookieUtils = authCookieUtils;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        //로그인 성공 시 스프링이 만들어준 인증 객체에서 "구글이 알려준 사용자 정보"를 꺼냄
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String googleId = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");

        LoginResponse loginResponse = authService.loginWithGoogle(googleId, email);

        //postman 테스트 시 사용
        //response.setStatus(HttpServletResponse.SC_OK);
        //response.setContentType("application/json;charset=UTF-8");
        //response.getWriter().write(objectMapper.writeValueAsString(loginResponse));
        
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieUtils.accessTokenCookie(loginResponse.accessToken(), Duration.ofMillis(expirationMs)).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieUtils.refreshTokenCookie(loginResponse.refreshToken(), Duration.ofMillis(refreshExpirationMs)).toString());

        // 쿠키 set 한 다음, redirect 전에:
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        response.sendRedirect(frontendUrl);
    }
}

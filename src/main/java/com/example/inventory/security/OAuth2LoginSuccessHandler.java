/**
 * OAuth2 로그인 성공 후 넘어온 사용자 정보 추출 google_id, email
 * UserRepository로 google_id 기준 조회 → 없으면 새로 생성(role 기본값 STAFF)
 * JwtProvider.generateToken(user)로 JWT 발급
 * { accessToken, user } 형태로 JSON 응답 작성 (일반 리다이렉트가 아니라 직접 응답 바디를 씀)
 */
package com.example.inventory.security;

import com.example.inventory.dto.response.LoginResponse;
import com.example.inventory.service.AuthService;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler{

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public OAuth2LoginSuccessHandler(
            AuthService authService,
            ObjectMapper objectMapper
    ) {
        this.authService = authService;
        this.objectMapper = objectMapper;
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

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(loginResponse));
    }
}

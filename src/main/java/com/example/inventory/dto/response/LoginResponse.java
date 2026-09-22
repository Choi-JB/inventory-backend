/**
 * Google OAuth2 로그인 성공 시 반환할 응답 형식
 * accessToken: JWT 토큰
 * user: 사용자 정보 (id, email, role)
 */
package com.example.inventory.dto.response;

public record LoginResponse(String accessToken, UserInfo user) {
    public record UserInfo(Long id, String email, String role) {}
}
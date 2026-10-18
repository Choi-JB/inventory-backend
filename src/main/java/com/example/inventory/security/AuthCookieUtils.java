package com.example.inventory.security;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

public class AuthCookieUtils {

    private AuthCookieUtils() {}  // 인스턴스화 방지 - 전부 static 메서드만 쓰는 유틸 클래스라서

    public static ResponseCookie accessTokenCookie(String token, Duration maxAge) {
        return ResponseCookie.from("accessToken", token)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
    
    public static ResponseCookie refreshTokenCookie(String token, Duration maxAge) {
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
     }
    
    public static ResponseCookie clearAccessTokenCookie() {
        return accessTokenCookie("", Duration.ZERO);
    }
    public static ResponseCookie clearRefreshTokenCookie() {
        return refreshTokenCookie("", Duration.ZERO);
    }
    
    public static String getCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(cookieName)) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
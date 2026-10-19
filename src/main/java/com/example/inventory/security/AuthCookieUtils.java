package com.example.inventory.security;

import java.time.Duration;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * access/refresh 쿠키 생성.
 * HttpOnly 는 항상 true. Secure 와 SameSite 는 app.cookie.* (프로필별 yaml) 를 따른다.
 */
@Component
public class AuthCookieUtils {

    private static final Set<String> SAME_SITE_VALUES = Set.of("Lax", "Strict", "None");

    private final boolean secure;
    private final String sameSite;

    public AuthCookieUtils(
            @Value("${app.cookie.secure}") boolean secure,
            @Value("${app.cookie.same-site}") String sameSite) {
        if (!SAME_SITE_VALUES.contains(sameSite)) {
            throw new IllegalStateException(
                    "app.cookie.same-site 는 Lax, Strict, None 중 하나여야 합니다: " + sameSite);
        }
        if ("None".equals(sameSite) && !secure) {
            throw new IllegalStateException("SameSite=None 은 Secure=true 일 때만 사용할 수 있습니다.");
        }
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public ResponseCookie accessTokenCookie(String token, Duration maxAge) {
        return build("accessToken", token, maxAge);
    }

    public ResponseCookie refreshTokenCookie(String token, Duration maxAge) {
        return build("refreshToken", token, maxAge);
    }

    public ResponseCookie clearAccessTokenCookie() {
        return accessTokenCookie("", Duration.ZERO);
    }

    public ResponseCookie clearRefreshTokenCookie() {
        return refreshTokenCookie("", Duration.ZERO);
    }

    public String getCookieValue(HttpServletRequest request, String cookieName) {
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

    private ResponseCookie build(String name, String token, Duration maxAge) {
        return ResponseCookie.from(name, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}

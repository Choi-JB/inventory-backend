package com.example.inventory.security;

import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    private SecurityUtils() {}  // 인스턴스화 방지 — 전부 static 메서드만 쓰는 유틸 클래스라서

    public static Long getCurrentUserId() {
        String userIdStr = SecurityContextHolder.getContext().getAuthentication().getName();
        return Long.valueOf(userIdStr);
    }
}
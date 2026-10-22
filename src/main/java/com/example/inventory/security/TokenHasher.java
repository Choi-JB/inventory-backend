package com.example.inventory.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 리프레시 토큰 원문을 DB에 넣지 않기 위한 해시.
 * 같은 토큰은 항상 같은 값이 나와야 행을 찾을 수 있다.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    /** SHA-256 해시를 16진수 문자열로 반환한다. */
    public static String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256를 사용할 수 없습니다.", e);
        }
    }
}

package com.example.inventory.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TokenSecurityIntegrationTest extends IntegrationTestSupport {

    private static final String TEST_SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void 만료된_access토큰은_401이다() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes());
        String expired = Jwts.builder()
                .subject("1")
                .claim("email", "staff@test.com")
                .claim("role", "STAFF")
                .claim("type", "access")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("accessToken", expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 서명을_바꾼_access토큰은_401이다() throws Exception {
        signUp("staff@test.com", "pass1234");
        String accessToken = loginCookies("staff@test.com", "pass1234")[0].getValue();
        String tampered = tamperSignature(accessToken);

        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("accessToken", tampered)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private static String tamperSignature(String token) {
        int last = token.length() - 1;
        char flipped = token.charAt(last) == 'a' ? 'b' : 'a';
        return token.substring(0, last) + flipped;
    }
}

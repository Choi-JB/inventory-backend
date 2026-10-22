package com.example.inventory.integration;

import com.example.inventory.entity.RefreshToken;
import com.example.inventory.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestSupport {

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Test
    void 회원가입후_로그인하면_HttpOnly쿠키가_내려오고_me가_STAFF를_반환한다() throws Exception {
        signUp("staff@test.com", "pass1234");

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"staff@test.com","password":"pass1234"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        List<String> setCookies = login.getResponse().getHeaders("Set-Cookie");
        assertThat(setCookies).anyMatch(header -> header.startsWith("accessToken=") && header.contains("HttpOnly"));
        assertThat(setCookies).anyMatch(header -> header.startsWith("refreshToken=") && header.contains("HttpOnly"));

        mockMvc.perform(get("/api/auth/me").cookie(authCookies(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("staff@test.com"))
                .andExpect(jsonPath("$.role").value("STAFF"));
    }

    @Test
    void 쿠키없이_me와_상품목록은_401이다() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 비밀번호가_틀리면_400이고_없는_이메일은_404이다() throws Exception {
        signUp("staff@test.com", "pass1234");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"staff@test.com","password":"wrong1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@test.com","password":"pass1234"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 로그아웃_응답은_쿠키를_만료시키고_쿠키가_없으면_me는_401이다() throws Exception {
        signUp("staff@test.com", "pass1234");
        Cookie[] cookies = loginCookies("staff@test.com", "pass1234");

        MvcResult logout = mockMvc.perform(post("/api/auth/logout").cookie(cookies))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        List<String> cleared = logout.getResponse().getHeaders("Set-Cookie");
        assertThat(cleared).anyMatch(header -> header.startsWith("accessToken=") && header.contains("Max-Age=0"));
        assertThat(cleared).anyMatch(header -> header.startsWith("refreshToken=") && header.contains("Max-Age=0"));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh쿠키로_토큰을_다시_받으면_me가_된다() throws Exception {
        signUp("staff@test.com", "pass1234");
        Cookie[] cookies = loginCookies("staff@test.com", "pass1234");

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(cookies))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(get("/api/auth/me").cookie(authCookies(refreshed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("staff@test.com"));
    }

    @Test
    void 로그아웃하면_이전_리프레시토큰으로_재발급할_수_없다() throws Exception {
        signUp("staff@test.com", "pass1234");
        Cookie[] cookies = loginCookies("staff@test.com", "pass1234");

        mockMvc.perform(post("/api/auth/logout").cookie(cookies))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh").cookie(cookies))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void 만료된_리프레시토큰은_재발급이_거절된다() throws Exception {
        signUp("staff@test.com", "pass1234");
        Cookie[] cookies = loginCookies("staff@test.com", "pass1234");
        RefreshToken stored = refreshTokenRepository.findAll().get(0);
        ReflectionTestUtils.setField(stored, "expiresAt", LocalDateTime.now().minusMinutes(1));
        refreshTokenRepository.save(stored);

        mockMvc.perform(post("/api/auth/refresh").cookie(cookies))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void 재발급에_쓴_토큰은_다시_쓸_수_없다() throws Exception {
        signUp("staff@test.com", "pass1234");
        Cookie[] cookies = loginCookies("staff@test.com", "pass1234");

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(cookies))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(post("/api/auth/refresh").cookie(cookies))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        mockMvc.perform(get("/api/auth/me").cookie(authCookies(refreshed)))
                .andExpect(status().isOk());
    }

    @Test
    void 짧은_비밀번호_회원가입은_400이다() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"short@test.com","password":"ab","passwordConfirm":"ab"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}

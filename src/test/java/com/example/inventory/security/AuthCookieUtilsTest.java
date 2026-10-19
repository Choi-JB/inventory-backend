package com.example.inventory.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthCookieUtilsTest {

    @Test
    void 로컬설정이면_HttpOnly이고_Secure는_빠지며_SameSite는_Lax() {
        AuthCookieUtils utils = new AuthCookieUtils(false, "Lax");

        String header = utils.refreshTokenCookie("abc", Duration.ofHours(9)).toString();

        assertThat(header).contains("refreshToken=abc");
        assertThat(header).contains("HttpOnly");
        assertThat(header).contains("SameSite=Lax");
        assertThat(header).doesNotContain("Secure");
    }

    @Test
    void 운영설정이면_발급과_삭제_쿠키가_같은_Secure와_SameSite를_쓴다() {
        AuthCookieUtils utils = new AuthCookieUtils(true, "Lax");

        String set = utils.accessTokenCookie("abc", Duration.ofMinutes(30)).toString();
        String clear = utils.clearAccessTokenCookie().toString();

        assertThat(set).contains("Secure");
        assertThat(set).contains("SameSite=Lax");
        assertThat(clear).contains("Secure");
        assertThat(clear).contains("SameSite=Lax");
        assertThat(clear).contains("Max-Age=0");
    }

    @Test
    void SameSite_None은_Secure가_아니면_거부한다() {
        assertThatThrownBy(() -> new AuthCookieUtils(false, "None"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void yaml은_기본_Secure가_false이고_prod_문서에서만_true() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));

        PropertySource<?> defaults = sources.stream()
                .filter(source -> source.getProperty("spring.config.activate.on-profile") == null)
                .findFirst()
                .orElseThrow();
        PropertySource<?> prod = sources.stream()
                .filter(source -> "prod".equals(String.valueOf(source.getProperty("spring.config.activate.on-profile"))))
                .findFirst()
                .orElseThrow();

        assertThat(defaults.getProperty("app.cookie.secure")).isEqualTo(false);
        assertThat(defaults.getProperty("app.cookie.same-site")).isEqualTo("Lax");
        assertThat(defaults.getProperty("frontend.url")).isEqualTo("http://localhost:3000");
        assertThat(prod.getProperty("app.cookie.secure")).isEqualTo(true);
        assertThat(prod.getProperty("app.cookie.same-site")).isEqualTo("Lax");
        assertThat(prod.getProperty("server.servlet.session.cookie.secure")).isEqualTo(true);
        assertThat(prod.getProperty("springdoc.swagger-ui.enabled")).isEqualTo(false);
    }
}

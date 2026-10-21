package com.example.inventory.integration;

import com.example.inventory.entity.User;
import com.example.inventory.enums.Role;
import com.example.inventory.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(IntegrationTestSupport.FakeVectorStoreConfig.class)
abstract class IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.execute("TRUNCATE TABLE refresh_tokens");
        jdbcTemplate.execute("TRUNCATE TABLE stock_transactions");
        jdbcTemplate.execute("TRUNCATE TABLE products");
        jdbcTemplate.execute("TRUNCATE TABLE categories");
        jdbcTemplate.execute("TRUNCATE TABLE users");
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    void signUp(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","passwordConfirm":"%s"}
                                """.formatted(email, password, password)))
                .andExpect(status().isCreated());
    }

    void createUser(String email, String password, Role role) {
        User user = userRepository.save(User.local(email, passwordEncoder.encode(password), role));
        user.assignDefaultUserName();
        userRepository.save(user);
    }

    Cookie[] loginCookies(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return authCookies(result);
    }

    static Cookie[] authCookies(MvcResult result) {
        List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
        return new Cookie[] {
                new Cookie("accessToken", cookieValue(setCookies, "accessToken")),
                new Cookie("refreshToken", cookieValue(setCookies, "refreshToken"))
        };
    }

    static String cookieValue(List<String> headers, String name) {
        return headers.stream()
                .filter(header -> header.startsWith(name + "="))
                .map(header -> header.substring(name.length() + 1, header.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(name + " 쿠키가 없습니다."));
    }

    @TestConfiguration
    static class FakeVectorStoreConfig {
        @Bean
        VectorStore vectorStore() {
            return new VectorStore() {
                @Override
                public void add(List<Document> documents) {
                }

                @Override
                public void delete(List<String> idList) {
                }

                @Override
                public void delete(org.springframework.ai.vectorstore.filter.Filter.Expression filterExpression) {
                }

                @Override
                public List<Document> similaritySearch(SearchRequest request) {
                    return List.of();
                }
            };
        }
    }
}

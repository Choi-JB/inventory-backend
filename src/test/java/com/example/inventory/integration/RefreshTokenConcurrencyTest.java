package com.example.inventory.integration;

import com.example.inventory.dto.request.LoginRequest;
import com.example.inventory.dto.request.RefreshRequest;
import com.example.inventory.entity.User;
import com.example.inventory.enums.Role;
import com.example.inventory.exception.InvalidTokenException;
import com.example.inventory.repository.RefreshTokenRepository;
import com.example.inventory.repository.UserRepository;
import com.example.inventory.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 리프레시 토큰으로 동시에 재발급하면 한 건만 새 토큰을 받는다.
 */
@SpringBootTest
@ActiveProfiles("pg")
@EnabledIfEnvironmentVariable(named = "PG_TEST_PASSWORD", matches = ".+")
@Import(IntegrationTestSupport.FakeVectorStoreConfig.class)
class RefreshTokenConcurrencyTest {

    @Autowired
    AuthService authService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE stock_transactions, products, categories, users, refresh_tokens
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void 같은_리프레시토큰을_동시에_재발급하면_한_건만_성공한다() throws Exception {
        User user = userRepository.save(User.local("refresh@test.com", passwordEncoder.encode("pass1234"), Role.STAFF));
        user.assignDefaultUserName();
        userRepository.save(user);
        String refreshToken = authService.login(new LoginRequest("refresh@test.com", "pass1234")).refreshToken();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());

        Thread first = new Thread(() -> refresh(refreshToken, ready, start, done, success, rejected, unexpected));
        Thread second = new Thread(() -> refresh(refreshToken, ready, start, done, success, rejected, unexpected));
        first.start();
        second.start();

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();

        assertThat(unexpected).isEmpty();
        assertThat(success).hasValue(1);
        assertThat(rejected).hasValue(1);
        assertThat(refreshTokenRepository.findAll())
                .filteredOn(token -> token.getRevokedAt() == null)
                .hasSize(1);
    }

    private void refresh(
            String refreshToken,
            CountDownLatch ready,
            CountDownLatch start,
            CountDownLatch done,
            AtomicInteger success,
            AtomicInteger rejected,
            List<Throwable> unexpected
    ) {
        try {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            authService.refresh(new RefreshRequest(refreshToken));
            success.incrementAndGet();
        } catch (InvalidTokenException e) {
            rejected.incrementAndGet();
        } catch (Throwable e) {
            unexpected.add(e);
        } finally {
            done.countDown();
        }
    }
}

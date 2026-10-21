package com.example.inventory.integration;

import com.example.inventory.entity.Category;
import com.example.inventory.entity.Product;
import com.example.inventory.entity.User;
import com.example.inventory.enums.Role;
import com.example.inventory.enums.Unit;
import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.repository.CategoryRepository;
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.repository.StockTransactionRepository;
import com.example.inventory.repository.UserRepository;
import com.example.inventory.service.StockTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.example.inventory.dto.response.StockTransactionResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재고 10에서 6개 출고를 두 스레드가 동시에 호출한다.
 * PostgreSQL 비관적 락이 동작하면 한 건만 저장되고 재고는 4가 된다.
 */
@SpringBootTest
@ActiveProfiles("pg")
@EnabledIfEnvironmentVariable(named = "PG_TEST_PASSWORD", matches = ".+")
@Import(IntegrationTestSupport.FakeVectorStoreConfig.class)
class StockOutConcurrencyTest {

    @Autowired
    StockTransactionService stockTransactionService;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    StockTransactionRepository stockTransactionRepository;

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
    void 동시에_재고를_넘는_출고를_하면_한_건만_성공한다() throws Exception {
        Category category = categoryRepository.save(new Category("음료", "설명", null));
        Product product = productRepository.save(
                new Product("원두", "SKU-CONCURRENT", category.getId(), Unit.EA, new BigDecimal("5000"), 1));
        product.increaseStock(10, new BigDecimal("1000"));
        productRepository.save(product);
        User user = userRepository.save(User.local("concurrent@test.com", "hash", Role.STAFF));
        long productId = product.getId();
        long userId = user.getId();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());

        Thread first = new Thread(() -> stockOut(productId, userId, "concurrent-a", ready, start, done, success, rejected, unexpected));
        Thread second = new Thread(() -> stockOut(productId, userId, "concurrent-b", ready, start, done, success, rejected, unexpected));
        first.start();
        second.start();

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();

        assertThat(unexpected).isEmpty();
        assertThat(success).hasValue(1);
        assertThat(rejected).hasValue(1);
        assertThat(productRepository.findById(productId).orElseThrow().getCurrentStock()).isEqualTo(4);
        assertThat(stockTransactionRepository.findAll()).hasSize(1);
    }

    @Test
    void 같은_요청키로_동시에_출고하면_재고는_한_번만_줄어든다() throws Exception {
        Category category = categoryRepository.save(new Category("음료", "설명", null));
        Product product = productRepository.save(
                new Product("원두", "SKU-SAME-KEY", category.getId(), Unit.EA, new BigDecimal("5000"), 1));
        product.increaseStock(10, new BigDecimal("1000"));
        productRepository.save(product);
        User user = userRepository.save(User.local("same-key@test.com", "hash", Role.STAFF));
        long productId = product.getId();
        long userId = user.getId();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        List<Long> ids = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());

        Thread first = new Thread(() -> stockOutSameKey(productId, userId, ready, start, done, success, ids, unexpected));
        Thread second = new Thread(() -> stockOutSameKey(productId, userId, ready, start, done, success, ids, unexpected));
        first.start();
        second.start();

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();

        assertThat(unexpected).isEmpty();
        assertThat(success).hasValue(2);
        assertThat(ids).hasSize(2);
        assertThat(ids.get(0)).isEqualTo(ids.get(1));
        assertThat(productRepository.findById(productId).orElseThrow().getCurrentStock()).isEqualTo(6);
        assertThat(stockTransactionRepository.findAll()).hasSize(1);
    }

    private void stockOutSameKey(
            long productId,
            long userId,
            CountDownLatch ready,
            CountDownLatch start,
            CountDownLatch done,
            AtomicInteger success,
            List<Long> ids,
            List<Throwable> unexpected
    ) {
        try {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            StockTransactionResponse response = stockTransactionService.stockOut(
                    productId, userId, 4, null, "같은키", "same-key");
            ids.add(response.id());
            success.incrementAndGet();
        } catch (Throwable e) {
            unexpected.add(e);
        } finally {
            done.countDown();
        }
    }

    private void stockOut(
            long productId,
            long userId,
            String idempotencyKey,
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
            stockTransactionService.stockOut(productId, userId, 6, null, "동시출고", idempotencyKey);
            success.incrementAndGet();
        } catch (InsufficientStockException e) {
            rejected.incrementAndGet();
        } catch (Throwable e) {
            unexpected.add(e);
        } finally {
            done.countDown();
        }
    }
}

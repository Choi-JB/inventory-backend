package com.example.inventory.integration;

import com.example.inventory.entity.Category;
import com.example.inventory.entity.Product;
import com.example.inventory.entity.StockTransaction;
import com.example.inventory.enums.Role;
import com.example.inventory.enums.Unit;
import com.example.inventory.repository.CategoryRepository;
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.repository.StockTransactionRepository;
import com.example.inventory.service.StockTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

class StockOutAtomicityIntegrationTest extends IntegrationTestSupport {

    @MockitoSpyBean
    StockTransactionRepository stockTransactionRepository;

    @Autowired
    StockTransactionService stockTransactionService;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Test
    void 출고_이력_저장이_실패하면_재고_감소도_롤백된다() {
        createUser("admin@test.com", "pass1234", Role.ADMIN);
        long userId = userRepository.findByEmail("admin@test.com").orElseThrow().getId();
        Category category = categoryRepository.save(new Category("음료", "설명", null));
        Product product = productRepository.save(
                new Product("원두", "SKU-ATOM", category.getId(), Unit.EA, new BigDecimal("5000"), 1));
        product.increaseStock(10, new BigDecimal("1000"));
        productRepository.save(product);
        long productId = product.getId();

        doThrow(new RuntimeException("이력 저장 실패"))
                .when(stockTransactionRepository)
                .save(any(StockTransaction.class));

        assertThatThrownBy(() -> stockTransactionService.stockOut(
                productId, userId, 4, new BigDecimal("5000"), "판매", "idem-atom"))
                .isInstanceOf(RuntimeException.class);

        Product reloaded = productRepository.findById(productId).orElseThrow();
        assertThat(reloaded.getCurrentStock()).isEqualTo(10);
        assertThat(stockTransactionRepository.findAll()).isEmpty();
    }
}

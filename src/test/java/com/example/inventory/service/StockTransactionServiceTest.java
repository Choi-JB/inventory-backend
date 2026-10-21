package com.example.inventory.service;

import com.example.inventory.dto.request.RollbackRequest;
import com.example.inventory.dto.response.ProfitLossResponse;
import com.example.inventory.dto.response.StockTransactionResponse;
import com.example.inventory.entity.Product;
import com.example.inventory.entity.StockTransaction;
import com.example.inventory.entity.User;
import com.example.inventory.enums.ConsumeType;
import com.example.inventory.enums.Role;
import com.example.inventory.enums.TransactionStatus;
import com.example.inventory.enums.TransactionType;
import com.example.inventory.enums.Unit;
import com.example.inventory.exception.AlreadyCanceledException;
import com.example.inventory.exception.IdempotencyConflictException;
import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.exception.NotFoundException;
import com.example.inventory.exception.RollbackConflictException;
import com.example.inventory.exception.RollbackNotAllowedException;
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.repository.StockTransactionRepository;
import com.example.inventory.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockTransactionServiceTest {

    @Mock
    private StockTransactionRepository stockTransactionRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private StockTransactionService stockTransactionService;

    @BeforeEach
    void 저장된_요청키가_없으면_새_거래로_진행한다() {
        lenient().when(stockTransactionRepository.findByIdempotencyKey(any()))
                .thenReturn(Optional.empty());
        lenient().when(transactionTemplate.execute(ArgumentMatchers.<TransactionCallback<StockTransactionResponse>>any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<StockTransactionResponse> callback = invocation.getArgument(0);
                    return callback.doInTransaction(new SimpleTransactionStatus());
                });
    }

    @Test
    void stockIn_재고를늘리고거래를저장한다() {
        Product product = productWithStock(1L, 0, "0");
        User user = user(10L, "STAFF-10");
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(stockTransactionRepository.save(any(StockTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransactionResponse response = stockTransactionService.stockIn(
                1L, 10L, 100, new BigDecimal("1000"), "입고", "idem-in");

        assertThat(product.getCurrentStock()).isEqualTo(100);
        assertThat(product.getCostPrice()).isEqualByComparingTo("1000.00");
        assertThat(response.userName()).isEqualTo("STAFF-10");
        assertThat(response.type()).isEqualTo(TransactionType.IN);
        assertThat(savedTransaction().getIdempotencyKey()).isEqualTo("idem-in");
    }

    @Test
    void stockOut_단가가없으면판매가를사용한다() {
        Product product = productWithStock(1L, 50, "500");
        User user = user(10L, "STAFF-10");
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(stockTransactionRepository.save(any(StockTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransactionResponse response = stockTransactionService.stockOut(
                1L, 10L, 10, null, "출고", "idem-out");

        assertThat(product.getCurrentStock()).isEqualTo(40);
        assertThat(response.unitPrice()).isEqualByComparingTo("3000");
        assertThat(response.type()).isEqualTo(TransactionType.OUT);
        assertThat(savedTransaction().getIdempotencyKey()).isEqualTo("idem-out");
    }

    @Test
    void stockOut_같은키면기존거래를반환하고_재고는_그대로다() {
        StockTransaction tx = transaction(7L, 1L, 10L, TransactionType.OUT, TransactionStatus.ACTIVE, 4, null);
        Product product = productWithStock(1L, 6, "500");
        User user = user(10L, "STAFF-10");
        when(stockTransactionRepository.findByIdempotencyKey("idem-7")).thenReturn(Optional.of(tx));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        StockTransactionResponse response = stockTransactionService.stockOut(
                1L, 10L, 4, null, "reason", "idem-7");

        assertThat(response.quantity()).isEqualTo(4);
        assertThat(product.getCurrentStock()).isEqualTo(6);
        verify(stockTransactionRepository, never()).save(any());
        verify(productRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void stockOut_같은키에_수량이_다르면_409이다() {
        StockTransaction tx = transaction(7L, 1L, 10L, TransactionType.OUT, TransactionStatus.ACTIVE, 4, null);
        when(stockTransactionRepository.findByIdempotencyKey("idem-7")).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> stockTransactionService.stockOut(1L, 10L, 9, null, "reason", "idem-7"))
                .isInstanceOf(IdempotencyConflictException.class);
        verify(stockTransactionRepository, never()).save(any());
        verify(productRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void stockOut_재고부족이면예외() {
        Product product = productWithStock(1L, 5, "500");
        User user = user(10L, "STAFF-10");
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> stockTransactionService.stockOut(1L, 10L, 10, null, "출고", "idem-out-fail"))
                .isInstanceOf(InsufficientStockException.class);
        verify(stockTransactionRepository, never()).save(any());
    }

    @Test
    void stockConsume_소비거래를생성한다() {
        Product product = productWithStock(1L, 20, "500");
        User user = user(10L, "STAFF-10");
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(stockTransactionRepository.save(any(StockTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransactionResponse response = stockTransactionService.stockConsume(
                1L, 10L, 5, "폐기", ConsumeType.DISCARD, "idem-consume");

        assertThat(product.getCurrentStock()).isEqualTo(15);
        assertThat(response.type()).isEqualTo(TransactionType.CONSUME);
        assertThat(response.consumeType()).isEqualTo(ConsumeType.DISCARD);
        assertThat(savedTransaction().getIdempotencyKey()).isEqualTo("idem-consume");
    }

    @Test
    void stockAdjustment_실사수량으로조정한다() {
        Product product = productWithStock(1L, 20, "500");
        User user = user(10L, "STAFF-10");
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(stockTransactionRepository.save(any(StockTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransactionResponse response = stockTransactionService.stockAdjustment(
                1L, 10L, 15, "실사", "idem-adjust");

        assertThat(product.getCurrentStock()).isEqualTo(15);
        assertThat(response.quantity()).isEqualTo(-5);
        assertThat(response.type()).isEqualTo(TransactionType.ADJUSTMENT);
        assertThat(savedTransaction().getIdempotencyKey()).isEqualTo("idem-adjust");
    }

    @Test
    void rollback_ADJUSTMENT는거부한다() {
        StockTransaction tx = transaction(1L, 1L, 10L, TransactionType.ADJUSTMENT, TransactionStatus.ACTIVE, 5, null);
        when(stockTransactionRepository.findById(1L)).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> stockTransactionService.rollback(10L, 1L, new RollbackRequest("사유")))
                .isInstanceOf(RollbackNotAllowedException.class)
                .hasMessageContaining("ADUSTMENT");
    }

    @Test
    void rollback_이미취소된거래는거부한다() {
        StockTransaction tx = transaction(1L, 1L, 10L, TransactionType.OUT, TransactionStatus.CANCELED, 5, null);
        when(stockTransactionRepository.findById(1L)).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> stockTransactionService.rollback(10L, 1L, new RollbackRequest("사유")))
                .isInstanceOf(AlreadyCanceledException.class);
    }

    @Test
    void rollback_상쇄거래는거부한다() {
        StockTransaction tx = transaction(1L, 1L, 10L, TransactionType.OUT, TransactionStatus.ACTIVE, 5, 99L);
        when(stockTransactionRepository.findById(1L)).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> stockTransactionService.rollback(10L, 1L, new RollbackRequest("사유")))
                .isInstanceOf(RollbackNotAllowedException.class)
                .hasMessageContaining("이미 롤백");
    }

    @Test
    void rollback_입고롤백시재고부족이면충돌() {
        StockTransaction tx = transaction(1L, 1L, 10L, TransactionType.IN, TransactionStatus.ACTIVE, 100, null);
        ReflectionTestUtils.setField(tx, "unitPrice", new BigDecimal("1000"));
        Product product = productWithStock(1L, 30, "1000");
        when(stockTransactionRepository.findById(1L)).thenReturn(Optional.of(tx));
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> stockTransactionService.rollback(10L, 1L, new RollbackRequest("입고 취소")))
                .isInstanceOf(RollbackConflictException.class)
                .hasMessageContaining("재고 수량이 부족");
    }

    @Test
    void rollback_출고를복구한다() {
        StockTransaction tx = transaction(1L, 1L, 10L, TransactionType.OUT, TransactionStatus.ACTIVE, 10, null);
        ReflectionTestUtils.setField(tx, "unitPrice", new BigDecimal("3000"));
        ReflectionTestUtils.setField(tx, "costPriceSnapshot", new BigDecimal("500"));
        Product product = productWithStock(1L, 40, "500");
        User user = user(10L, "ADMIN-10");
        when(stockTransactionRepository.findById(1L)).thenReturn(Optional.of(tx));
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(stockTransactionRepository.save(any(StockTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        StockTransactionResponse response = stockTransactionService.rollback(
                10L, 1L, new RollbackRequest("출고 취소"));

        assertThat(product.getCurrentStock()).isEqualTo(50);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.CANCELED);
        assertThat(tx.getCanceledBy()).isEqualTo(10L);
        assertThat(response.reversalOfId()).isEqualTo(1L);
        assertThat(response.userName()).isEqualTo("ADMIN-10");
    }

    @Test
    void searchById_등록자와취소자이름을채운다() {
        StockTransaction tx = transaction(1L, 1L, 10L, TransactionType.OUT, TransactionStatus.CANCELED, 5, null);
        ReflectionTestUtils.setField(tx, "canceledBy", 20L);
        Product product = productWithStock(1L, 10, "500");
        User creator = user(10L, "STAFF-10");
        User canceler = user(20L, "ADMIN-20");
        when(stockTransactionRepository.findById(1L)).thenReturn(Optional.of(tx));
        when(userRepository.findById(10L)).thenReturn(Optional.of(creator));
        when(userRepository.findById(20L)).thenReturn(Optional.of(canceler));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        StockTransactionResponse response = stockTransactionService.searchById(1L);

        assertThat(response.userName()).isEqualTo("STAFF-10");
        assertThat(response.canceledByName()).isEqualTo("ADMIN-20");
    }

    @Test
    void search_페이지의유저상품을배치조회한다() {
        StockTransaction tx = transaction(1L, 1L, 10L, TransactionType.IN, TransactionStatus.ACTIVE, 5, null);
        Product product = productWithStock(1L, 10, "500");
        User user = user(10L, "STAFF-10");
        PageRequest pageable = PageRequest.of(0, 10);
        when(stockTransactionRepository.findAll(ArgumentMatchers.<Specification<StockTransaction>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(tx), pageable, 1));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(userRepository.findAllById(any())).thenReturn(List.of(user));

        Page<StockTransactionResponse> result = stockTransactionService.search(
                null, null, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).userName()).isEqualTo("STAFF-10");
        assertThat(result.getContent().get(0).productName()).isEqualTo("아메리카노");
    }

    @Test
    void getProfitLoss_매출원가소비손실을집계한다() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 31, 23, 59);
        StockTransaction out = transaction(1L, 1L, 10L, TransactionType.OUT, TransactionStatus.ACTIVE, 2, null);
        ReflectionTestUtils.setField(out, "unitPrice", new BigDecimal("3000"));
        ReflectionTestUtils.setField(out, "costPriceSnapshot", new BigDecimal("1000"));
        StockTransaction consume = transaction(2L, 1L, 10L, TransactionType.CONSUME, TransactionStatus.ACTIVE, 1, null);
        ReflectionTestUtils.setField(consume, "costPriceSnapshot", new BigDecimal("1000"));
        Product product = productWithStock(1L, 10, "1000");

        when(stockTransactionRepository.findAll(ArgumentMatchers.<Specification<StockTransaction>>any()))
                .thenReturn(List.of(out))
                .thenReturn(List.of(consume));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));

        ProfitLossResponse response = stockTransactionService.getProfitLoss(start, end, null);

        // 매출 6000, 원가 2000, 판매이익 4000, 소비손실 1000, 최종이익 3000
        assertThat(response.totalRevenue()).isEqualByComparingTo("6000");
        assertThat(response.totalCost()).isEqualByComparingTo("2000");
        assertThat(response.totalProfit()).isEqualByComparingTo("4000");
        assertThat(response.consumeLoss()).isEqualByComparingTo("1000");
        assertThat(response.netProfit()).isEqualByComparingTo("3000");
        assertThat(response.byProduct()).hasSize(1);
        assertThat(response.byProduct().get(0).net()).isEqualByComparingTo("3000");
    }

    @Test
    void stockIn_상품없으면NotFound() {
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockTransactionService.stockIn(1L, 10L, 1, BigDecimal.ONE, null, "idem-missing-product"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("상품");
    }

    private StockTransaction savedTransaction() {
        ArgumentCaptor<StockTransaction> captor = ArgumentCaptor.forClass(StockTransaction.class);
        verify(stockTransactionRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Product productWithStock(Long id, int stock, String costPrice) {
        Product product = new Product("아메리카노", "SKU-1", 10L, Unit.EA, new BigDecimal("3000"), 5);
        ReflectionTestUtils.setField(product, "id", id);
        ReflectionTestUtils.setField(product, "currentStock", stock);
        ReflectionTestUtils.setField(product, "costPrice", new BigDecimal(costPrice));
        return product;
    }

    private static User user(Long id, String name) {
        User user = User.google("google-" + id, id + "@test.com", Role.STAFF);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "name", name);
        return user;
    }

    private static StockTransaction transaction(
            Long id,
            Long productId,
            Long userId,
            TransactionType type,
            TransactionStatus status,
            int quantity,
            Long reversalOfId
    ) {
        StockTransaction tx = new StockTransaction(
                productId, userId, quantity, null, null, "reason", type, status, null, reversalOfId, "idem-" + id);
        ReflectionTestUtils.setField(tx, "id", id);
        ReflectionTestUtils.setField(tx, "createdAt", LocalDateTime.now());
        return tx;
    }
}

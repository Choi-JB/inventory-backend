/**
 * IN,OUT,CONSUME,ADJUSTMENT,ROLLBACK, 조회, 손익계산
 * 인벤토리 관리 서비스
 */
package com.example.inventory.service;

import org.springframework.stereotype.Service;
import com.example.inventory.repository.StockTransactionRepository;
import com.example.inventory.dto.response.StockTransactionResponse;
import com.example.inventory.entity.StockTransaction;
import jakarta.transaction.Transactional;
import com.example.inventory.exception.NotFoundException;
import com.example.inventory.dto.request.RollbackRequest;
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.entity.Product;
import java.math.BigDecimal;
import com.example.inventory.enums.TransactionType;
import com.example.inventory.enums.TransactionStatus;
import com.example.inventory.enums.ConsumeType;
import com.example.inventory.exception.RollbackConflictException;
import com.example.inventory.exception.RollbackNotAllowedException;
import com.example.inventory.exception.AlreadyCanceledException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import com.example.inventory.dto.response.ProfitLossResponse;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;

@Service
public class StockTransactionService {
    private final StockTransactionRepository stockTransactionRepository;
    private final ProductRepository productRepository;

    public StockTransactionService(StockTransactionRepository stockTransactionRepository,
            ProductRepository productRepository) {
        this.stockTransactionRepository = stockTransactionRepository;
        this.productRepository = productRepository;
    }

    /** 거래내역 조회 조건 설정 Specification */
    // 상품 id로 거래내역 조회
    private Specification<StockTransaction> productId(Long productId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("productId"), productId);
    }

    // 거래 유형으로 거래내역 조회
    private Specification<StockTransaction> type(TransactionType type) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("type"), type);
    }

    // 거래 상태로 거래내역 조회
    private Specification<StockTransaction> status(TransactionStatus status) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status);
    }

    // 날짜 범위로 거래내역 조회
    private Specification<StockTransaction> dateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.between(root.get("createdAt"), startDate, endDate);
    }

    // reversalOfId 가 비워있는 것만
    private Specification<StockTransaction> excludeReversal() {
        return (root, query, cb) -> cb.isNull(root.get("reversalOfId"));
    }

    // 손익계산 조회 (날짜 범위, 상품 id, 거래 유형)
    private List<StockTransaction> findProfitLossTransactions(LocalDateTime startDate, LocalDateTime endDate, Long productId, TransactionType type) {
        Specification<StockTransaction> spec = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        spec = spec.and(dateRange(startDate, endDate));
        spec = spec.and(type(type));
        spec = spec.and(status(TransactionStatus.ACTIVE));
        spec = spec.and(excludeReversal());
        if ( productId != null ) {
            spec = spec.and(productId(productId));
        }
        return stockTransactionRepository.findAll(spec);
    }

    //------------------------------------

    // 거래내역 조회 (상품 id, 거래 유형, 거래 상태, 날짜 범위, 페이지네이션)
    public Page<StockTransactionResponse> search(Long productId, TransactionType type, TransactionStatus status,
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Specification<StockTransaction> spec = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (productId != null) {
            spec = spec.and(productId(productId));
        }
        if (type != null) {
            spec = spec.and(type(type));
        }
        if (status != null) {
            spec = spec.and(status(status));
        }
        if (startDate != null && endDate != null) {
            spec = spec.and(dateRange(startDate, endDate));
        }

        Page<StockTransaction> stockTransactions = stockTransactionRepository.findAll(spec, pageable);
        return stockTransactions.map(StockTransactionResponse::from);
    }

    // 거래내역 조회 (내역 id로 조회)
    public StockTransactionResponse searchById(Long id) {
        StockTransaction stockTransaction = stockTransactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("거래내역을 찾을 수 없습니다: " + id));
        return StockTransactionResponse.from(stockTransaction);
    }

    
    // 재고 입고
    @Transactional
    public StockTransactionResponse stockIn(Long productId, Long userId, Integer quantity, BigDecimal unitPrice,
            String reason) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다: " + productId));

        // 상품 재고 증가
        product.increaseStock(quantity, unitPrice);

        // 재고 입고 거래 생성
        StockTransaction stockTransaction = new StockTransaction(productId, userId, quantity, unitPrice,
                product.getCostPrice(), reason, TransactionType.IN, TransactionStatus.ACTIVE, null, null);
        stockTransactionRepository.save(stockTransaction);

        // 재고 입고 거래 내역 반환
        return StockTransactionResponse.from(stockTransaction);
    }

    // 재고 출고
    @Transactional
    public StockTransactionResponse stockOut(Long productId, Long userId, Integer quantity, BigDecimal unitPrice,
            String reason) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다: " + productId));

        // 단가 설정 안된 경우 판매가격 사용
        if (unitPrice == null) {
            unitPrice = product.getSellingPrice();
        }

        // 상품 재고 감소
        product.decreaseStock(quantity);

        // 재고 출고 거래 생성
        StockTransaction stockTransaction = new StockTransaction(productId, userId, quantity, unitPrice,
                product.getCostPrice(), reason, TransactionType.OUT, TransactionStatus.ACTIVE, null, null);
        stockTransactionRepository.save(stockTransaction);

        // 재고 출고 거래 내역 반환
        return StockTransactionResponse.from(stockTransaction);

    }

    // 재고 소비
    @Transactional
    public StockTransactionResponse stockConsume(Long productId, Long userId, Integer quantity, String reason,
            ConsumeType consumeType) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다: " + productId));

        // 상품 재고 감소
        product.decreaseStock(quantity);

        // 재고 소비 거래 생성
        StockTransaction stockTransaction = new StockTransaction(productId, userId, quantity, null,
                product.getCostPrice(), reason, TransactionType.CONSUME, TransactionStatus.ACTIVE, consumeType, null);
        stockTransactionRepository.save(stockTransaction);

        // 재고 소비 거래 내역 반환
        return StockTransactionResponse.from(stockTransaction);
    }

    // 재고 조정
    @Transactional
    public StockTransactionResponse stockAdjustment(Long productId, Long userId, Integer actualQuantity,
            String reason) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다: " + productId));

        // 기존 재고 수량 저장
        Integer oldStock = product.getCurrentStock();
        // 재고 조정
        product.adjustStock(actualQuantity);
        // 조정 수량 계산
        Integer quantity = actualQuantity - oldStock;
        // 재고 조정 거래 생성
        StockTransaction stockTransaction = new StockTransaction(productId, userId, quantity, null, null, reason,
                TransactionType.ADJUSTMENT, TransactionStatus.ACTIVE, null, null);
        stockTransactionRepository.save(stockTransaction);

        // 재고 조정 거래 내역 반환
        return StockTransactionResponse.from(stockTransaction);
    }

    /**
     * 재고 복구(롤백)
     * ADUSTMENT 거래내역은 복구 안됨
     * 
     * @param userId
     * @param transactionId
     * @param rollbackRequest
     * @return
     */
    @Transactional
    public StockTransactionResponse rollback(Long userId, Long transactionId, RollbackRequest rollbackRequest) {

        // 1.롤백할 거래내역 조회
        StockTransaction stockTransaction = stockTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("재고 거래 내역을 찾을 수 없습니다: " + transactionId));

        // 1-1.ADUSTMENT 거래내역은 복구 안됨
        if (stockTransaction.getType() == TransactionType.ADJUSTMENT) {
            throw new RollbackNotAllowedException("ADUSTMENT 거래내역은 복구할 수 없습니다: " + transactionId);
        }
        // 1-2.CANCELED 거래내역은 복구 안됨
        if (stockTransaction.getStatus() == TransactionStatus.CANCELED) {
            throw new AlreadyCanceledException("이미 취소된 거래내역입니다: " + transactionId);
        }

        // 1-3. 이미 롤백된 거래내역은 복구 안됨
        if (stockTransaction.getReversalOfId() != null) {
            throw new RollbackNotAllowedException("이미 롤백된 거래내역입니다: " + transactionId);
        }

        // 2. 롤백할 상품 조회
        Product product = productRepository.findByIdForUpdate(stockTransaction.getProductId())
                .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다: " + stockTransaction.getProductId()));

        // 2-1. 입고 롤백일 경우
        if (stockTransaction.getType() == TransactionType.IN) {
            // 2-1-1. 재고 수량이 부족할 경우
            if (product.getCurrentStock() < stockTransaction.getQuantity()) {
                throw new RollbackConflictException("재고 수량이 부족합니다: " + product.getId() + " (복구 수량: "
                        + stockTransaction.getQuantity() + ", 재고 수량: " + product.getCurrentStock() + ")");
            }
            product.reverseIncreaseStock(stockTransaction.getQuantity(), stockTransaction.getUnitPrice());
        } else { // 2-2. 출고,소비 롤백일 경우
            product.restoreStock(stockTransaction.getQuantity());
        }

        // 3. 새로운 롤백 내역 생성
        StockTransaction reversalStockTransaction = new StockTransaction(stockTransaction.getProductId(), userId,
                stockTransaction.getQuantity(), stockTransaction.getUnitPrice(), null, rollbackRequest.reason(),
                stockTransaction.getType(), TransactionStatus.ACTIVE, null, stockTransaction.getId());
        stockTransactionRepository.save(reversalStockTransaction);

        // 4. 기존 거래내역 취소 처리
        stockTransaction.cancel(userId);
        stockTransactionRepository.save(stockTransaction);

        return StockTransactionResponse.from(reversalStockTransaction);
    }

    // 수익/손실 조회
    @Transactional
    public ProfitLossResponse getProfitLoss(LocalDateTime startDate, LocalDateTime endDate, Long productId) {
        //출고 거래내역 조회
        List<StockTransaction> outTransactions = findProfitLossTransactions(startDate, endDate, productId, TransactionType.OUT);
        //소비 거래내역 조회
        List<StockTransaction> consumeTransactions = findProfitLossTransactions(startDate, endDate, productId, TransactionType.CONSUME);

        //출고 매출 계산 (출고 거래내역의 단가 x 수량)
        BigDecimal totalRevenue = outTransactions.stream()
                .map(stockTransaction -> stockTransaction.getUnitPrice().multiply(new BigDecimal(stockTransaction.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        //출고 비용 계산 (출고 거래내역의 비용 x 수량)
        BigDecimal totalCost = outTransactions.stream()
                .map(stockTransaction -> stockTransaction.getCostPriceSnapshot().multiply(new BigDecimal(stockTransaction.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        //매출 - 비용 = 수익
        BigDecimal totalProfit = totalRevenue.subtract(totalCost);

        //소비 손실 계산 (소비 거래내역의 비용 x 수량)
        BigDecimal consumeLoss = consumeTransactions.stream()
                .map(stockTransaction -> stockTransaction.getCostPriceSnapshot().multiply(new BigDecimal(stockTransaction.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);


        //제품별 수익 계산
        Map<Long, BigDecimal> profitByProduct = outTransactions.stream()
            .collect(Collectors.groupingBy(
                StockTransaction::getProductId, 
                Collectors.reducing(BigDecimal.ZERO, 
                    st->st.getUnitPrice().multiply(BigDecimal.valueOf(st.getQuantity())).subtract(st.getCostPriceSnapshot().multiply(BigDecimal.valueOf(st.getQuantity()))),
                    BigDecimal::add
                )));
        
        //제품별 손실 계산
        Map<Long, BigDecimal> lossByProduct = consumeTransactions.stream()
            .collect(Collectors.groupingBy(
                StockTransaction::getProductId, 
                Collectors.reducing(BigDecimal.ZERO, 
                    st->st.getCostPriceSnapshot().multiply(BigDecimal.valueOf(st.getQuantity())),
                    BigDecimal::add
                )));

        //제품별 수익/손실 계산을 위한 제품 ID 조회
        Set<Long> productIds = new HashSet<>();
        productIds.addAll(profitByProduct.keySet());
        productIds.addAll(lossByProduct.keySet());

        //제품별 수익/손실 계산 (상품 조회 후 수익/손실 계산)
        List<ProfitLossResponse.ByProduct> byProduct = productIds.stream()
        .map(id -> {
            Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다: " + id));
            BigDecimal profit = profitByProduct.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal loss = lossByProduct.getOrDefault(id, BigDecimal.ZERO);
            return new ProfitLossResponse.ByProduct(id, product.getName(), profit, loss);
        })
        .toList();

        return new ProfitLossResponse(totalRevenue, totalCost, totalProfit, consumeLoss, byProduct);
    }


}

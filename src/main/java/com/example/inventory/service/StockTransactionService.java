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
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.entity.Product;
import java.math.BigDecimal;
import com.example.inventory.enums.TransactionType;
import com.example.inventory.enums.TransactionStatus;
import com.example.inventory.enums.ConsumeType;

@Service
public class StockTransactionService {
    private final StockTransactionRepository stockTransactionRepository;
    private final ProductRepository productRepository;

    public StockTransactionService(StockTransactionRepository stockTransactionRepository, ProductRepository productRepository) {
        this.stockTransactionRepository = stockTransactionRepository;
        this.productRepository = productRepository;
    }

    //재고 입고
    @Transactional
    public StockTransactionResponse stockIn(Long productId, Long userId, Integer quantity, BigDecimal unitPrice, String reason) {
        Product product = productRepository.findByIdForUpdate(productId)
            .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다: " + productId));

        //상품 재고 증가    
        product.increaseStock(quantity, unitPrice);

        //재고 입고 거래 생성
        StockTransaction stockTransaction = new StockTransaction(productId, userId, quantity, unitPrice, product.getCostPrice(), reason, TransactionType.IN, TransactionStatus.ACTIVE, null);
        stockTransactionRepository.save(stockTransaction);

        //재고 입고 거래 내역 반환
        return StockTransactionResponse.from(stockTransaction);
    }

    //재고 출고
    @Transactional
    public StockTransactionResponse stockOut(Long productId, Long userId, Integer quantity, BigDecimal unitPrice, String reason) {
        Product product = productRepository.findByIdForUpdate(productId)
            .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다: " + productId));

        //단가 설정 안된 경우 판매가격 사용
        if(unitPrice==null){
            unitPrice = product.getSellingPrice();
        }

        //상품 재고 감소
        product.decreaseStock(quantity);

        //재고 출고 거래 생성
        StockTransaction stockTransaction = new StockTransaction(productId, userId, quantity, unitPrice, product.getCostPrice(), reason, TransactionType.OUT, TransactionStatus.ACTIVE, null);
        stockTransactionRepository.save(stockTransaction);

        //재고 출고 거래 내역 반환
        return StockTransactionResponse.from(stockTransaction);

    }


    //재고 소비
    @Transactional
    public StockTransactionResponse stockConsume(Long productId, Long userId, Integer quantity, String reason, ConsumeType consumeType) {
        Product product = productRepository.findByIdForUpdate(productId)
            .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다: " + productId));

        //상품 재고 감소
        product.decreaseStock(quantity);

        //재고 소비 거래 생성
        StockTransaction stockTransaction = new StockTransaction(productId, userId, quantity, null, product.getCostPrice(), reason, TransactionType.CONSUME, TransactionStatus.ACTIVE, consumeType);
        stockTransactionRepository.save(stockTransaction);

        //재고 소비 거래 내역 반환
        return StockTransactionResponse.from(stockTransaction);
    }

    //재고 조정
    @Transactional
    public StockTransactionResponse stockAdjustment(Long productId, Long userId, Integer actualQuantity, String reason) {
        Product product = productRepository.findByIdForUpdate(productId)
            .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다: " + productId));

        //기존 재고 수량 저장
        Integer oldStock = product.getCurrentStock();
        //재고 조정
        product.adjustStock(actualQuantity);
        //조정 수량 계산
        Integer quantity = actualQuantity - oldStock;
        //재고 조정 거래 생성
        StockTransaction stockTransaction = new StockTransaction(productId, userId, quantity, null, null, reason, TransactionType.ADJUSTMENT, TransactionStatus.ACTIVE, null);
        stockTransactionRepository.save(stockTransaction);

        //재고 조정 거래 내역 반환
        return StockTransactionResponse.from(stockTransaction);
    }
    
}

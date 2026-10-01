package com.example.inventory.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.inventory.service.StockTransactionService;
import com.example.inventory.service.ProductService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import com.example.inventory.dto.request.StockInRequest;
import com.example.inventory.dto.request.StockOutRequest;
import com.example.inventory.dto.request.StockConsumeRequest;
import com.example.inventory.dto.request.StockAdjustmentRequest;
import com.example.inventory.dto.response.StockTransactionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import com.example.inventory.dto.request.RollbackRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import com.example.inventory.enums.TransactionType;
import com.example.inventory.enums.TransactionStatus;
import com.example.inventory.dto.response.ProductResponse;
import com.example.inventory.dto.response.ProfitLossResponse;
import com.example.inventory.security.SecurityUtils;

@RestController
@RequestMapping("/api/stock")
public class StockTransactionController {
    private final StockTransactionService stockTransactionService;
    private final ProductService productService;

    public StockTransactionController(StockTransactionService stockTransactionService, ProductService productService) {
        this.productService = productService;
        this.stockTransactionService = stockTransactionService;
    }

    // POST /api/stock/in [재고 입고]
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PostMapping("/in")
    public ResponseEntity<StockTransactionResponse> stockIn(@Valid @RequestBody StockInRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();

        return ResponseEntity.status(HttpStatus.CREATED).body(stockTransactionService.stockIn(request.productId(),
                userId, request.quantity(), request.unitPrice(), request.reason()));
    }

    // POST /api/stock/out [재고 출고]
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PostMapping("/out")
    public ResponseEntity<StockTransactionResponse> stockOut(@Valid @RequestBody StockOutRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();

        return ResponseEntity.status(HttpStatus.CREATED).body(stockTransactionService.stockOut(request.productId(),
                userId, request.quantity(), request.unitPrice(), request.reason()));
    }

    // POST /api/stock/consume [재고 소비]
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PostMapping("/consume")
    public ResponseEntity<StockTransactionResponse> stockConsume(@Valid @RequestBody StockConsumeRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(stockTransactionService.stockConsume(request.productId(),
                userId, request.quantity(), request.reason(), request.consumeType()));
    }

    // POST /api/stock/adjustment [재고 조정]
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/adjustment")
    public ResponseEntity<StockTransactionResponse> stockAdjustment(
            @Valid @RequestBody StockAdjustmentRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(stockTransactionService
                .stockAdjustment(request.productId(), userId, request.actualQuantity(), request.reason()));
    }

    // POST /api/stock/transactions/{id}/rollback} [롤백] - ADMIN만 가능, 200 OK + 롤백된 거래내역
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/transactions/{id}/rollback")
    public ResponseEntity<StockTransactionResponse> rollbackStockTransaction(
            @PathVariable Long id, //거래내역 id
            @Valid @RequestBody RollbackRequest request) { //복구 이유
                Long userId = SecurityUtils.getCurrentUserId();
        StockTransactionResponse stockTransaction = stockTransactionService.rollback(userId, id, request);
        return ResponseEntity.status(HttpStatus.OK).body(stockTransaction);
    }

    //GET /api/stock/transactions [거래내역 조회]
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @GetMapping("/transactions")
    public ResponseEntity<Page<StockTransactionResponse>> getTransactions(
        @RequestParam(required = false) Long productId,
        @RequestParam(required = false) TransactionType type,
        @RequestParam(required = false) TransactionStatus status,
        @RequestParam(required = false) LocalDateTime startDate,
        @RequestParam(required = false) LocalDateTime endDate,
        Pageable pageable) {
        return ResponseEntity.status(HttpStatus.OK).body(stockTransactionService.search(productId, type, status, startDate, endDate, pageable));
    }

    //GET /api/stock/transactions/{id} [id로 거래내역 조회]
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @GetMapping("/transactions/{id}")
    public ResponseEntity<StockTransactionResponse> getTransactionById(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK).body(stockTransactionService.searchById(id));
    }

    //GET /api/stock/low-stock [재고 부족 상품 조회]
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @GetMapping("/low-stock")
    public ResponseEntity<Page<ProductResponse>> getLowStockProducts(
        Pageable pageable) {
            return ResponseEntity.ok(productService.search(null, null, true, pageable));
    }

    //GET /api/stock/profit-loss [수익/손실 조회]
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @GetMapping("/profit-loss")
    public ResponseEntity<ProfitLossResponse> getProfitLoss(
        @RequestParam(required = true) LocalDateTime startDate,
        @RequestParam(required = true) LocalDateTime endDate,
        @RequestParam(required = false) Long productId) {
        return ResponseEntity.status(HttpStatus.OK).body(stockTransactionService.getProfitLoss(startDate, endDate, productId));
    }

}

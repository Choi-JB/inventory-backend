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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "재고 트랜잭션", description = "입고/출고/소비/조정/롤백, 거래내역 조회, 재고부족 조회, 손익계산")
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
    @Operation(summary = "재고 입고", description = "ADMIN/STAFF. 가중평균으로 상품 매입단가(costPrice)를 갱신하고 재고를 증가시킵니다.")
    @PostMapping("/in")
    public ResponseEntity<StockTransactionResponse> stockIn(@Valid @RequestBody StockInRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();

        return ResponseEntity.status(HttpStatus.CREATED).body(stockTransactionService.stockIn(request.productId(),
                userId, request.quantity(), request.unitPrice(), request.reason()));
    }

    // POST /api/stock/out [재고 출고]
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "재고 출고", description = "ADMIN/STAFF. unitPrice를 생략하면 상품의 sellingPrice를 사용합니다. 재고가 부족하면 409(INSUFFICIENT_STOCK).")
    @PostMapping("/out")
    public ResponseEntity<StockTransactionResponse> stockOut(@Valid @RequestBody StockOutRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();

        return ResponseEntity.status(HttpStatus.CREATED).body(stockTransactionService.stockOut(request.productId(),
                userId, request.quantity(), request.unitPrice(), request.reason()));
    }

    // POST /api/stock/consume [재고 소비]
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "재고 자체소비", description = "ADMIN/STAFF. 폐기/내부사용/샘플(consumeType)로 재고를 차감하며 매출이 없는 전액 손실로 집계됩니다.")
    @PostMapping("/consume")
    public ResponseEntity<StockTransactionResponse> stockConsume(@Valid @RequestBody StockConsumeRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(stockTransactionService.stockConsume(request.productId(),
                userId, request.quantity(), request.reason(), request.consumeType()));
    }

    // POST /api/stock/adjustment [재고 조정]
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "재고 조정", description = "ADMIN 전용. 실사 수량(actualQuantity)으로 재고를 맞추고 차이를 ±로 기록합니다. reason 필수, 손익 계산에서는 제외됩니다.")
    @PostMapping("/adjustment")
    public ResponseEntity<StockTransactionResponse> stockAdjustment(
            @Valid @RequestBody StockAdjustmentRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(stockTransactionService
                .stockAdjustment(request.productId(), userId, request.actualQuantity(), request.reason()));
    }

    // POST /api/stock/transactions/{id}/rollback} [롤백] - ADMIN만 가능, 200 OK + 롤백된 거래내역
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "거래 롤백", description = "ADMIN 전용. 상쇄 트랜잭션을 만들고 원본을 CANCELED 처리합니다. 조정 거래/이미 취소된 거래/롤백 거래는 불가, 재고가 음수가 되면 409(ROLLBACK_CONFLICT).")
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
    @Operation(summary = "거래내역 목록 조회", description = "productId, type, status, 기간(startDate~endDate) 조건을 선택적으로 조합하고 페이징합니다. 롤백 트랜잭션도 포함됩니다.")
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
    @Operation(summary = "거래내역 단건 조회", description = "롤백 전 확인용. 존재하지 않는 id면 404.")
    @GetMapping("/transactions/{id}")
    public ResponseEntity<StockTransactionResponse> getTransactionById(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK).body(stockTransactionService.searchById(id));
    }

    //GET /api/stock/low-stock [재고 부족 상품 조회]
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "재고 부족 상품 목록", description = "currentStock이 minStockLevel 이하인 상품을 페이징하여 반환합니다.")
    @GetMapping("/low-stock")
    public ResponseEntity<Page<ProductResponse>> getLowStockProducts(
        Pageable pageable) {
            return ResponseEntity.ok(productService.search(null, null, true, pageable));
    }

    //GET /api/stock/profit-loss [수익/손실 조회]
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "손익 계산", description = "기간(startDate, endDate 필수) 내 ACTIVE 상태의 원본 출고/소비 거래만 집계합니다(롤백된 거래와 롤백 트랜잭션 제외). productId로 특정 상품만 볼 수 있습니다.")
    @GetMapping("/profit-loss")
    public ResponseEntity<ProfitLossResponse> getProfitLoss(
        @RequestParam(required = true) LocalDateTime startDate,
        @RequestParam(required = true) LocalDateTime endDate,
        @RequestParam(required = false) Long productId) {
        return ResponseEntity.status(HttpStatus.OK).body(stockTransactionService.getProfitLoss(startDate, endDate, productId));
    }

}

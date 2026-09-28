package com.example.inventory.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.inventory.service.StockTransactionService;
import org.springframework.security.access.prepost.PreAuthorize;
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

@RestController
@RequestMapping("/api/stock")
public class StockTransactionController {
    private final StockTransactionService stockTransactionService;

    public StockTransactionController(StockTransactionService stockTransactionService) {
        this.stockTransactionService = stockTransactionService;
    }

    // POST /api/stock/in [재고 입고]
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PostMapping("/in")
    public ResponseEntity<StockTransactionResponse> stockIn(@Valid @RequestBody StockInRequest request) {
        Long userId = getCurrentUserId();
        
        return ResponseEntity.status(HttpStatus.CREATED).body(stockTransactionService.stockIn(request.productId(), userId, request.quantity(), request.unitPrice(), request.reason()));
    }

    // POST /api/stock/out [재고 출고]
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PostMapping("/out")
    public ResponseEntity<StockTransactionResponse> stockOut(@Valid @RequestBody StockOutRequest request) {
        Long userId = getCurrentUserId();

        return ResponseEntity.status(HttpStatus.CREATED).body(stockTransactionService.stockOut(request.productId(), userId, request.quantity(), request.unitPrice(), request.reason()));
    }

    // POST /api/stock/consume [재고 소비]
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PostMapping("/consume")
    public ResponseEntity<StockTransactionResponse> stockConsume(@Valid @RequestBody StockConsumeRequest request) {
        Long userId = getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(stockTransactionService.stockConsume(request.productId(), userId, request.quantity(), request.reason(), request.consumeType()));
    }

    // POST /api/stock/adjustment [재고 조정]
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/adjustment")
    public ResponseEntity<StockTransactionResponse> stockAdjustment(@Valid @RequestBody StockAdjustmentRequest request) {
        Long userId = getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(stockTransactionService.stockAdjustment(request.productId(), userId, request.actualQuantity(), request.reason()));
    }

    // 현재 사용자 ID 가져오기
    private Long getCurrentUserId() {
        String userIdStr = SecurityContextHolder.getContext().getAuthentication().getName();
        return Long.valueOf(userIdStr);
    }

}

package com.example.inventory.dto.response;

import java.math.BigDecimal;
import com.example.inventory.enums.TransactionType;
import com.example.inventory.enums.TransactionStatus;
import com.example.inventory.entity.StockTransaction;
import java.time.LocalDateTime;
import com.example.inventory.enums.ConsumeType;
import com.example.inventory.enums.Unit;
import com.example.inventory.entity.Product;

public record StockTransactionResponse(
    Long id,
    Long productId,
    Long userId,
    TransactionType type,
    TransactionStatus status,
    Integer quantity,
    BigDecimal unitPrice,
    BigDecimal costPriceSnapshot,
    String reason,
    Long reversalOfId,
    Long canceledBy,
    LocalDateTime canceledAt,
    LocalDateTime createdAt,
    ConsumeType consumeType,
    String productName,
    Unit productUnit
) {
    public static StockTransactionResponse from(StockTransaction stockTransaction, Product product) {
        return new StockTransactionResponse(
            stockTransaction.getId(),
            stockTransaction.getProductId(),
            stockTransaction.getUserId(),
            stockTransaction.getType(),
            stockTransaction.getStatus(),
            stockTransaction.getQuantity(),
            stockTransaction.getUnitPrice(),
            stockTransaction.getCostPriceSnapshot(),
            stockTransaction.getReason(),
            stockTransaction.getReversalOfId(),
            stockTransaction.getCanceledBy(),
            stockTransaction.getCanceledAt(),
            stockTransaction.getCreatedAt(),
            stockTransaction.getConsumeType(),
            product.getName(),
            product.getUnit()
        );
    }
}

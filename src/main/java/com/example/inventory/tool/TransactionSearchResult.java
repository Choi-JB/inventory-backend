/**
 * TransactionSearchResult - searchTransactions tool 결과
 * TransactionResponse를 그대로 넘기지 않고, 답변에 필요한 필드만 사람이 읽는 형태로 축약 (챗봇명세서 3.1)
 */
package com.example.inventory.tool;

import java.util.List;
import java.time.format.DateTimeFormatter;
import com.example.inventory.dto.response.StockTransactionResponse;
import com.example.inventory.enums.TransactionStatus;
import com.example.inventory.enums.TransactionType;

public record TransactionSearchResult(
    long totalElements,
    List<Item> transactions
) {
    public record Item(
        Long id,
        String date,
        String productName,
        String type,
        String quantity,
        String unitPrice,
        String status,
        String reason
    ) {
        public static Item from(StockTransactionResponse r) {
            int signed = signedQuantity(r);
            return new Item(
                r.id(),
                r.createdAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                r.productName(),
                typeLabel(r),
                (signed > 0 ? "+" : "") + ToolFormatter.formatQuantity(signed, r.productUnit()),
                r.unitPrice() == null ? null : ToolFormatter.formatUnitPrice(r.unitPrice(), r.productUnit()),
                r.status() == TransactionStatus.ACTIVE ? "정상" : "취소됨",
                r.reason()
            );
        }

        /**
         * 거래 유형 라벨 생성
         * @param r 거래 정보
         * @return 거래 유형 라벨
         */
        private static String typeLabel(StockTransactionResponse r) {
            String label = switch (r.type()) {
                case IN -> "입고";
                case OUT -> "출고";
                case CONSUME -> "자체소비";
                case ADJUSTMENT -> "재고조정";
            };
            String consumeLabel = "";

            if(r.consumeType() != null) {
                consumeLabel = switch (r.consumeType()) {
                    case DISCARD -> "폐기";
                    case INTERNAL_USE -> "내부 사용";
                    case SAMPLE -> "샘플 제공";
                };
                label += "(" + consumeLabel + ")";
            }

            if(r.reversalOfId() != null) {
                label += " 취소";
            }
            return label;
        }

        /**
         * 거래 수량 계산
         * @param r 거래 정보
         * @return 거래 수량 (양수: 입고, 음수: 출고, 재고조정: 수량 그대로)
         */
        private static int signedQuantity(StockTransactionResponse r){
            int quantity = r.quantity();
            if(r.type() == TransactionType.ADJUSTMENT) {
                return quantity;
            } 

            int signed = 1;
            if(r.type() == TransactionType.OUT || r.type() == TransactionType.CONSUME) {
                signed *= -1;
            }

            if(r.reversalOfId() != null) {
                signed *= -1;
            }
            return quantity * signed;
        }
    }
    
}

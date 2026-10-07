/**
 * ProductSearchResult - searchProducts tool 결과
 * ProductResponse를 그대로 넘기지 않고, 답변에 필요한 필드만 사람이 읽는 형태로 축약 (챗봇명세서 3.1)
 * - 수량/가격은 ToolFormatter로 변환한 문자열 → Gemini가 단위 환산·계산을 하지 않게 함
 * - categoryId, costPrice 등 답변에 필요 없는 필드는 제외 → 토큰 절약
 */
package com.example.inventory.tool;

import com.example.inventory.dto.response.ProductResponse;
import java.util.List;

public record ProductSearchResult(
        long totalElements,     // 전체 검색 결과 수 (products는 최대 20건이라 "외 N건" 안내용)
        List<Item> products
) {
    public record Item(
            Long id,
            String name,
            String sku,
            String stock,           // 현재 재고 (예: "5kg")
            String minStock,        // 최소 재고 (예: "1kg")
            boolean lowStock,       // 재고 부족 여부 — 서버가 판단
            String sellingPrice     // 기준 판매가 (예: "25,000원/kg")
    ) {
        public static Item from(ProductResponse p) {
            return new Item(
                    p.id(),
                    p.name(),
                    p.sku(),
                    ToolFormatter.formatQuantity(p.currentStock(), p.unit()),
                    ToolFormatter.formatQuantity(p.minStockLevel(), p.unit()),
                    // ProductService.isLowStock()과 같은 조건이어야 함 (재고 부족 목록과 답이 어긋나지 않게)
                    p.currentStock() <= p.minStockLevel(),
                    ToolFormatter.formatUnitPrice(p.sellingPrice(), p.unit())
            );
        }
    }
}

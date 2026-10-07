/**
 * ProfitLossResult - getProfitLoss tool 결과
 * ProfitLossResponse의 금액을 표시 문자열로 바꾸고, 오해하기 쉬운 필드명을 바꿔서 전달 (챗봇명세서 3.1)
 * - totalProfit → salesProfit: 소비 손실이 빠진 "판매 이익"이라 "총이익"으로 오해하지 않게 함
 * - 최종 이익(순이익)은 netProfit — 손익 화면과 같은 값 (금액 계산은 서버 한 곳에서만)
 */
package com.example.inventory.tool;

import com.example.inventory.dto.response.ProfitLossResponse;
import java.time.LocalDate;
import java.util.List;

public record ProfitLossResult(
        String period,          // 조회 기간 (예: "2026-10-01 ~ 2026-10-07")
        String totalRevenue,    // 매출 (출고 판매가 × 수량 합)
        String totalCost,       // 판매 원가 (출고 당시 매입가 × 수량 합)
        String salesProfit,     // 판매 이익 = 매출 − 판매 원가 (소비 손실 미반영)
        String consumeLoss,     // 소비 손실 (폐기·내부사용·샘플)
        String netProfit,       // 최종 이익 = 판매 이익 − 소비 손실
        int byProductCount,     // 손익이 발생한 전체 상품 수 (byProduct는 최대 20건이라 "외 N개" 안내용)
        List<Item> byProduct    // 상품별 손익, 최종 이익(net) 내림차순 — 서버 정렬 그대로
) {
    // 목록 tool 공통 규칙: 최대 20건 (챗봇명세서 3.1)
    private static final int MAX_ITEMS = 20;

    public record Item(
            Long productId,
            String productName,
            String salesProfit,     // 상품별 판매 이익
            String consumeLoss,     // 상품별 소비 손실
            String net              // 상품별 최종 이익
    ) {
        public static Item from(ProfitLossResponse.ByProduct p) {
            return new Item(
                    p.productId(),
                    p.productName(),
                    ToolFormatter.formatWon(p.profit()),
                    ToolFormatter.formatWon(p.loss()),
                    ToolFormatter.formatWon(p.net())
            );
        }
    }

    public static ProfitLossResult from(ProfitLossResponse r, LocalDate startDate, LocalDate endDate) {
        List<Item> items = r.byProduct().stream()
                .limit(MAX_ITEMS)       // 이미 net 내림차순이라 앞에서 자르면 상위 20개
                .map(Item::from)
                .toList();

        return new ProfitLossResult(
                startDate + " ~ " + endDate,
                ToolFormatter.formatWon(r.totalRevenue()),
                ToolFormatter.formatWon(r.totalCost()),
                ToolFormatter.formatWon(r.totalProfit()),
                ToolFormatter.formatWon(r.consumeLoss()),
                ToolFormatter.formatWon(r.netProfit()),
                r.byProduct().size(),
                items
        );
    }
}

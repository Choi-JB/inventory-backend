package com.example.inventory.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ProfitLossResponse(
    //출고매출 [OUT 목록의 unitPrice x quantity 합]
    BigDecimal totalRevenue,
    //매입비용 [OUT 목록의 costPriceSnapshot x quantity 합]
    BigDecimal totalCost,
    //판매 이익(소비 손실 제외) : totalRevenue - totalCost
    BigDecimal totalProfit,
    //소비 손실 : [CONSUME 목록의 costPriceSnapshot x quantity 합] 
    BigDecimal consumeLoss,
    //최종 이익(판매 이익 - 소비 손실) : totalProfit − consumeLoss
    BigDecimal netProfit,
    //출고 거래내역 목록
    List<ByProduct> byProduct
) {
    public record ByProduct(
        //상품 ID
        Long productId,
        //상품명
        String productName,
        //수익
        BigDecimal profit,
        //손실
        BigDecimal loss,
        //판매 이익 - 소비 손실
        BigDecimal net
    ) {
    }
}

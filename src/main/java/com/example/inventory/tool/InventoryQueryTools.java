/**
 * InventoryQueryTools - 재고 조회 도구
 * 재고 조회 도구를 제공합니다.
 */
package com.example.inventory.tool;

import com.example.inventory.dto.response.ProductResponse;
import com.example.inventory.service.ProductService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import com.example.inventory.dto.response.CategoryTreeResponse;
import com.example.inventory.service.CategoryService;
import com.example.inventory.service.StockTransactionService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.example.inventory.dto.response.ProfitLossResponse;

@Component
public class InventoryQueryTools {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final StockTransactionService stockTransactionService;

    private static final Logger log = LoggerFactory.getLogger(InventoryQueryTools.class);

    public InventoryQueryTools(ProductService productService, CategoryService categoryService, StockTransactionService stockTransactionService) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.stockTransactionService = stockTransactionService;
    }

    @Tool(description = "상품명 또는 SKU로 상품을 검색해 현재 재고, 최소 재고, 재고 부족 여부, 판매가를 조회한다")
    public ProductSearchResult searchProducts(
            @ToolParam(description = "상품명 또는 SKU 일부", required = false) String keyword,
            @ToolParam(description = "true면 재고 부족 상품만 조회", required = false) Boolean lowStockOnly,
            @ToolParam(description = "getCategoryTree로 찾은 id", required = false) Long categoryId) {
        // 어떤 tool이 어떤 값으로 호출됐는지 로그 남기기 (라우팅 확인용)
        log.info("searchProducts 호출: keyword={}, lowStockOnly={}, categoryId={}", keyword, lowStockOnly, categoryId);
        // productService.search(...) 호출
        //         - Pageable은 Gemini가 줄 수 없으니 직접 만들기: PageRequest.of(0, 20) (명세서 3.1 "20건 고정")
        Pageable pageable = PageRequest.of(0, 20);

        Page<ProductResponse> page = productService.search(keyword, categoryId, lowStockOnly, pageable);

        List<ProductSearchResult.Item> items = page.getContent().stream()
                .map(ProductSearchResult.Item::from)
                .collect(Collectors.toList());
        return new ProductSearchResult(page.getTotalElements(), items);
    }

    @Tool(description = """
        전체 카테고리 계층(id, 이름, 하위 카테고리)을 조회한다. 
        카테고리 이름으로 상품을 찾을 때 먼저 호출해 id를 얻고, 그 id로 searchProducts를 호출한다.""")
    public List<CategoryTreeResponse> getCategoryTree() {
        log.info("getCategoryTree 호출");
        List<CategoryTreeResponse> categoryTree = categoryService.getTree();
        return categoryTree;
    }

    @Tool(description = """
        출고 매출, 매입비용, 판매 이익, 소비 손실, 최종 이익을 조회한다. 
        상품 ID가 있으면 해당 상품의 손익만 조회한다.
        조회 기간은 시작일부터 종료일까지(포함)이다.
        순이익·최종 이익을 물으면 netProfit으로 답한다.""")
    public ProfitLossResult getProfitLoss(
        @ToolParam(description = "조회 시작일, yyyy-MM-dd 형식 (예: 2026-10-01)") LocalDate startDate,
        @ToolParam(description = "조회 종료일, yyyy-MM-dd 형식 (예: 2026-10-07)") LocalDate endDate,
        @ToolParam(description = "searchProducts로 찾은 상품 id", required = false) Long productId
    ) {
        log.info("getProfitLoss 호출: startDate={}, endDate={}, productId={}", startDate, endDate, productId);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);
        ProfitLossResponse profitLoss = stockTransactionService.getProfitLoss(startDateTime, endDateTime, productId);

        return ProfitLossResult.from(profitLoss, startDate, endDate);
    }
}
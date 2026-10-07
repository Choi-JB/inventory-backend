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

@Component
public class InventoryQueryTools {

    // ProductService 주입 (필드 + 생성자, 컨트롤러에서 서비스 주입한 것과 같은 방식)
    private final ProductService productService;
    private static final Logger log = LoggerFactory.getLogger(InventoryQueryTools.class);

    public InventoryQueryTools(ProductService productService) {
        this.productService = productService;
    }

    @Tool(description = "상품명 또는 SKU로 상품을 검색해 현재 재고, 최소 재고, 재고 부족 여부, 판매가를 조회한다")
    public ProductSearchResult searchProducts(
            @ToolParam(description = "상품명 또는 SKU 일부", required = false) String keyword,
            @ToolParam(description = "true면 재고 부족 상품만 조회", required = false) Boolean lowStockOnly) {
        // 어떤 tool이 어떤 값으로 호출됐는지 로그 남기기 (라우팅 확인용)
        log.info("searchProducts 호출: keyword={}, lowStockOnly={}", keyword, lowStockOnly);
        // productService.search(...) 호출
        //         - categoryId는 이번엔 안 받으니 null
        //         - Pageable은 Gemini가 줄 수 없으니 직접 만들기: PageRequest.of(0, 20) (명세서 3.1 "20건 고정")
        Pageable pageable = PageRequest.of(0, 20);
        
        Page<ProductResponse> page = productService.search(keyword, null, lowStockOnly, pageable);

        List<ProductSearchResult.Item> items = page.getContent().stream()
                .map(ProductSearchResult.Item::from)
                .collect(Collectors.toList());
        return new ProductSearchResult(page.getTotalElements(), items);
    }
}
package com.example.inventory.tool;

import org.springframework.stereotype.Component;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.model.ToolContext;

@Component
public class ManualSearchTools {

    private final VectorStore vectorStore;
    private final double similarityThreshold;
    private static final Logger log = LoggerFactory.getLogger(ManualSearchTools.class);

    public ManualSearchTools(VectorStore vectorStore, @Value("${chat.manual.similarity-threshold}") double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.similarityThreshold = similarityThreshold;
    }

    @Tool(description = "시스템 사용 방법, 업무 절차, 규칙, 권한을 물을 때 업무 매뉴얼을 검색한다. 재고 수량·거래 내역·손익 같은 데이터 조회에는 사용하지 않는다.")
    public ManualSearchResult searchManual(
            @ToolParam(description = "사용자 질문의 의도를 담은 완전한 문장 (예: 잘못 등록한 출고를 취소하는 방법)") String query,
            ToolContext toolContext
        ) {
        // 1: 호출 로그 (query)
        log.info("searchManual 호출: query={}", query);
        // 2: similaritySearch — topK 4, 임계값은 일단 0.0 (매뉴얼 검색 결과 최대 4개)
        List<Document> docs = vectorStore.similaritySearch(
            SearchRequest.builder()
                    .query(query)
                    .topK(4)
                    .similarityThreshold(similarityThreshold)
                    .build());
        // 3: 조각마다 점수 로그 (source, section, score) ← 임계값 측정용
        SourceCollector collector = SourceCollector.from(toolContext);
        for (Document doc : docs) {
            log.info("  score={} source={} section={}",
                    doc.getScore(), doc.getMetadata().get("source"), doc.getMetadata().get("section"));
            collector.add((String) doc.getMetadata().get("source"), (String) doc.getMetadata().get("section"));
        }
        // 4: Document → ManualSearchResult.Item 변환 후 반환
        return new ManualSearchResult(docs.stream()
            .map(ManualSearchResult.Item::from)
            .toList());
    }
}

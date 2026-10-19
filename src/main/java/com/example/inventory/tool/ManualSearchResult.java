/**
 * ManualSearchResult - searchManuals tool 결과
 * Document를 그대로 넘기지 않고, 답변에 필요한 필드만 사람이 읽는 형태로 축약 (챗봇명세서 3.1)
 * - source, section, content는 원본 그대로 넘김 (챗봇명세서 3.1)
 */
package com.example.inventory.tool;

import java.util.List;
import org.springframework.ai.document.Document;

public record ManualSearchResult( List<Item> results ) {
    public record Item(String source, String section, String content) {
        public static Item from(Document doc) {
            String source = (String) doc.getMetadata().get("source");
            String section = (String) doc.getMetadata().get("section");
            String content = doc.getText();
            return new Item(source, section, content);
        }
    }
}

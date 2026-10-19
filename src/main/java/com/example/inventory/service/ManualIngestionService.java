/*
 * 매뉴얼 전체 재적재. 저장한 조각 수 반환
 * 마크다운 하나를 ## 단위 조각으로 자름
 */
package com.example.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ai.vectorstore.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;

@Service
public class ManualIngestionService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private static final Logger log = LoggerFactory.getLogger(ManualIngestionService.class);

    public ManualIngestionService(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 매뉴얼 전체 재적재. 저장한 조각 수 반환 */
    @Transactional
    public int reindex() throws IOException {
        // 1: classpath:manuals/*.md 읽기
        Resource[] resources = new PathMatchingResourcePatternResolver()
            .getResources("classpath:manuals/*.md");     // manuals 폴더의 .md 전부
        List<Document> documents = new ArrayList<>();
        // 각 resource에서 파일 제목과 내용 읽기
        for (Resource resource : resources) {
            String filename = resource.getFilename();                                // "rollback.md"
            String content = resource.getContentAsString(StandardCharsets.UTF_8);   // 파일 전체 내용
            // 2: 파일마다 split(...) 호출해서 Document 목록 하나로 모으기
            documents.addAll(split(filename, content));
        }
        if (documents.isEmpty()) {
            throw new IllegalStateException("적재할 매뉴얼 조각이 없습니다.");
        }
        // 3: DELETE FROM manual_embeddings
        jdbcTemplate.update("DELETE FROM manual_embeddings");
        // 4: vectorStore.add(...)
        vectorStore.add(documents);
        // 5: 로그 (조각 수) + 조각 수 반환
        log.info("매뉴얼 적재 완료: 파일 {}개, 조각 {}개", resources.length, documents.size());
        return documents.size();
    }

    /** 마크다운 하나를 ## 단위 조각으로 자름 */
    List<Document> split(String filename, String markdown) {
        // 2번 규칙대로 — markdown.lines()로 한 줄씩
        //       파일 제목, 지금 섹션 제목, 지금 본문(StringBuilder)을 변수로 들고 다니기
        //       조각 완성: text = 파일제목 + " > " + 섹션제목 + "\n" + 본문, metadata = source, section
        List<Document> documents = new ArrayList<>();   //조각을 모을 리스트
        String fileTitle = "";
        String sectionTitle = "";
        StringBuilder text = new StringBuilder();

        for (String line : markdown.lines().toList()) {
            if (line.startsWith("## ")) {
                addSection(documents, filename, fileTitle, sectionTitle, text);   //이전 섹션 조각 완성
                sectionTitle = line.substring(3).trim();    //새 섹션 제목
                text = new StringBuilder();    //새 본문 초기화
            } else if (line.startsWith("# ")) {
                fileTitle = line.substring(2).trim();
            }
            else {
                text.append(line).append("\n");
            }
        }
        addSection(documents, filename, fileTitle, sectionTitle, text);   //마지막 섹션 조각 완성
        return documents;
    }

    /** 섹션 조각 완성 */
    static void addSection(List<Document> documents, String filename, String fileTitle, String sectionTitle, StringBuilder text) {
        if(sectionTitle.isEmpty() || text.toString().isBlank()) return;
        if(fileTitle.isEmpty()) fileTitle = filename;
        
        documents.add(new Document(fileTitle + " > " + sectionTitle + "\n" + text.toString().trim(), Map.of("source", filename, "section", sectionTitle)));
    }
}
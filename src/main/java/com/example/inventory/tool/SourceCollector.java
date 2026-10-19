/**
 * SourceCollector - 이번 요청에서 searchManual이 찾은 매뉴얼 출처를 모으는 "바구니" (챗봇명세서 6장 sources)
 *
 * tool은 ChatClient.call() 안에서 Spring AI가 실행하므로 ChatService는 어떤 매뉴얼이 검색됐는지 알 수 없음.
 * 그래서 ChatService가 요청마다 이 객체를 새로 만들어 ToolContext로 건네고, searchManual이 여기에 기록한 뒤,
 * call()이 끝나면 ChatService가 꺼내서 응답의 sources로 사용.
 *
 * Spring 빈이 아님(@Component 없음): 빈은 서버 전체에 하나뿐이라, 빈에 기록하면 동시에 질문한 사용자들의 출처가 섞임.
 * 반드시 요청마다 new로 만들 것.
 */
package com.example.inventory.tool;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.ai.chat.model.ToolContext;

public class SourceCollector {

    // ToolContext에 담을 때 쓰는 이름. ChatService(넣는 쪽)와 from()(꺼내는 쪽)이 같은 값을 써야 해서 상수로 둠
    public static final String KEY = "sources";

    // LinkedHashSet: 넣은 순서 유지 + 중복 제거 (같은 조각이 두 번 검색돼도 한 번만)
    private final Set<Source> sources = new LinkedHashSet<>();

    // record라 source·section이 같으면 같은 값으로 취급됨(equals/hashCode 자동) → Set에서 중복 제거가 동작
    public record Source(String source, String section) {}

    public void add(String source, String section) {
        sources.add(new Source(source, section));
    }

    public List<Source> getSources() {
        return List.copyOf(sources);    // 바깥에서 수정할 수 없는 복사본
    }

    /**
     * tool에서 바구니를 꺼내는 도우미 (형변환 코드를 tool마다 반복하지 않게)
     * ToolContext가 없거나 바구니가 안 들어 있으면(예: ChatClient를 거치지 않고 tool을 직접 호출) 빈 바구니를 돌려줌
     * → 검색 자체는 정상 동작하고 출처만 기록되지 않음. ChatService에서 toolContext를 빠뜨리면 sources가 항상 []로 나오니 테스트로 확인할 것.
     */
    public static SourceCollector from(ToolContext toolContext) {
        if (toolContext != null && toolContext.getContext().get(KEY) instanceof SourceCollector collector) {
            return collector;
        }
        return new SourceCollector();
    }
}

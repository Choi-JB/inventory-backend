package com.example.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.ai.chat.client.ChatClient;
import com.example.inventory.tool.InventoryQueryTools;
import com.example.inventory.tool.ManualSearchTools;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.example.inventory.exception.ChatRateLimitedException;
import com.example.inventory.exception.ChatUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.time.ZoneId;
import com.example.inventory.dto.request.ChatRequest;
import com.example.inventory.dto.request.ChatHistoryMessage;
import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import com.example.inventory.dto.response.ChatResponse;     // 우리 DTO ⚠️ Spring AI의 ChatResponse 아님
import com.example.inventory.tool.SourceCollector;           // 방금 만든 클래스
import java.util.Map;                                        // 자바 기본
import java.util.ArrayList;
import java.util.Collections;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    // 시스템 프롬프트 (챗봇명세서 2.1) — 규칙마다 테스트에서 발견한 문제가 근거
    // 오늘 날짜는 서버 기동 시점에 고정되지 않도록 chat()에서 매 요청마다 붙임
    private static final String SYSTEM_PROMPT = """
        너는 재고관리 시스템의 조회 전용 도우미다. 항상 한국어로 답한다.

        ## 할 수 있는 일
        - 상품의 현재 재고, 최소 재고, 재고 부족 여부, 판매가 조회
        - 카테고리별 상품 조회
        - 입고·출고·자체소비·재고조정 거래 이력 조회
        - 기간별 손익(매출, 판매 이익, 소비 손실, 최종 이익) 조회
        - 시스템 사용 방법, 업무 절차·규칙, 역할별 권한 안내 (업무 매뉴얼 기준)

        ## 할 수 없는 일
        - 입고·출고·자체소비·재고조정 등록, 거래 롤백, 상품·카테고리 등록·수정·삭제 등 데이터를 바꾸는 작업
          → "해당 작업은 화면에서 진행해 주세요"라고 안내한다.
        - 재고관리와 관계없는 질문에는 답하지 말고, 위의 할 수 있는 일을 짧게 안내한다.

        ## 데이터 원칙
        - 재고, 거래, 손익 수치는 반드시 tool 결과로만 답한다. 추측하거나 지어내지 않는다.
        - tool 결과의 수량과 금액은 표시된 그대로 쓴다. 단위를 바꾸거나 직접 계산하지 않는다.
        - 판매가·단가는 "15,000원/kg", "45원/개"처럼 기준 단위까지 그대로 쓴다. "/kg", "/L", "/개"를 빼고 "15,000원"으로만 쓰지 않는다.
        - 합계나 총량(예: 이번 달 총 출고량)은 직접 더하지 않는다. "합계는 제공하지 않습니다"라고 안내하고, 필요하면 건수(totalElements)만 알려준다.
        - 이익을 물으면 최종 이익(netProfit)으로 답한다.

        ## 업무 매뉴얼 안내
        - 사용 방법, 절차, 규칙, 권한을 묻는 질문은 반드시 searchManual 결과에 근거해 답한다.
        - 검색된 매뉴얼에 없는 내용은 일반 상식이나 추측으로 보충하지 않는다.
        - 매뉴얼에 없는 안내처(도움말, 고객센터, 문의처 등)를 만들어 내지 않는다. 다른 사람에게 요청하라고 안내할 때는 매뉴얼에 나온 대상(예: ADMIN)만 언급한다.
        - 각 항목은 해당 매뉴얼 조각의 내용으로만 설명한다. 다른 조각의 표현을 섞지 않는다.
        - 검색 결과가 없거나, 검색된 내용이 질문과 관련이 없으면 "매뉴얼에 없는 내용입니다"라고 답한다. 비슷해 보이는 다른 절차를 대신 안내하지 않는다.
        - 절차는 필요한 단계를 순서대로 간결하게 쓴다. 이때는 1~2문장 제한을 따르지 않아도 된다.
        - 데이터 조회와 절차 안내가 함께 필요한 질문은 두 종류의 tool을 모두 사용한다.

        ## 답변 작성
        - 사용자는 tool 결과를 볼 수 없다. "위와 같습니다", "아래를 참고하세요"처럼 tool 결과를 가리키지 말고, 필요한 내용을 답변에 직접 쓴다.
        - 단일 정보(상품 하나의 재고, 기간 손익 하나)는 1~2문장으로 답한다.
        - 여러 건은 한 줄에 하나씩 "- "로 시작하는 목록으로 쓰고, 최대 10건까지만 보여준다. 더 있으면 마지막에 "외 N건"을 덧붙인다.
        - 거래 목록의 한 줄은 "날짜 · 유형 · 수량" 순서로 쓴다. 상태가 "취소됨"이면 줄 끝에 "(취소됨)"을 붙인다.
        - 유형이 "입고 취소"처럼 "취소"로 끝나는 거래는 원래 거래를 되돌린 거래다. 새로 입고·출고한 것으로 설명하지 않는다.

        ## 검색이 애매할 때
        - 상품 검색 결과가 0건이면 검색어를 바꿔 다시 검색하지 말고, 해당 상품을 찾을 수 없다고 답한다.
        - 비슷한 상품이 여러 개 검색되면 후보 이름을 보여주고 어느 상품인지 되묻는다.
        - 손익처럼 기간이 필요한 질문에 기간이 없으면 기간을 되묻는다.
        """;

    public ChatService(ChatClient.Builder builder, InventoryQueryTools inventoryQueryTools, ManualSearchTools manualSearchTools) {
        this.chatClient = builder
            .defaultTools(inventoryQueryTools, manualSearchTools)
            .build();
    }

    /**
     * 채팅 서비스
     * @param message 사용자 메시지
     * @return 채팅 응답
     */
    public ChatResponse chat(ChatRequest request) {
        List<Message> previous = toMessages(request.history());
        SourceCollector sources = new SourceCollector();

        long start = System.currentTimeMillis();
        try{
            String response = this.chatClient.prompt()
                .system(SYSTEM_PROMPT + "\n오늘 날짜: " + LocalDate.now(ZoneId.of("Asia/Seoul")))
                .messages(previous)
                .user(request.message())
                .toolContext(Map.of(SourceCollector.KEY, sources)) 
                .call()
                .content();
            log.info("채팅 소요 시간: {}ms", System.currentTimeMillis()-start);
            return new ChatResponse(response, sources.getSources());
        } catch (RuntimeException e) {
            ApiException apiException = findApiException(e);
            if (apiException == null) {
                if (findGenAiIOException(e) != null) {
                    log.warn("Gemini 호출 실패: GenAiIOException, {}ms", System.currentTimeMillis()-start, e);
                    throw new ChatUnavailableException("AI 응답이 일시적으로 지연되고 있습니다. 잠시 후 다시 시도해주세요.");
                }
                throw e;
            }
            log.warn("Gemini 호출 실패: code={}, {}ms", apiException.code(), System.currentTimeMillis()-start, e);
            if (apiException.code() == 429) {
                throw new ChatRateLimitedException("요청이 많아 잠시 후 다시 시도해주세요.");
            }
            else if (apiException.code() >= 500){
                throw new ChatUnavailableException("AI 응답이 일시적으로 지연되고 있습니다. 잠시 후 다시 시도해주세요.");
            } else {
                throw e;
            }
            
        }
    }

    /**
     * 예외에서 API 예외 찾기
     * @param e 예외
     * @return API 예외
     */
    private ApiException findApiException(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof ApiException) {
                return (ApiException) t;
            }
            t = t.getCause();
        }
        return null;
    }

    private GenAiIOException findGenAiIOException(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof GenAiIOException) {
                return (GenAiIOException) t;
            }
            t = t.getCause();
        }
        return null;
    }

    /**
     * 채팅 이력 메시지를 Message 리스트로 변환
     * @param history 채팅 이력 메시지
     * @return Message 리스트
     */
    private List<Message> toMessages(List<ChatHistoryMessage> history) {
        // 1: history가 null 이면 빈 리스트 반환
        if (history == null) {
            return Collections.emptyList();
        }
        // 2: 10개 넘으면 마지막 10개만 — subList(size - 10, size)
        List<ChatHistoryMessage> limited = history.subList(Math.max(0, history.size() - 10), history.size());
        // 3: role이 "user"면 UserMessage, "assistant"면 AssistantMessage로 변환
        List<Message> messages = new ArrayList<>(limited.size());
        for (ChatHistoryMessage message : limited) {
            if (message.role().equals("user")) {
                messages.add(new UserMessage(message.content()));
            } else {
                messages.add(new AssistantMessage(message.content()));
            }
        }
        return messages;
    }
}
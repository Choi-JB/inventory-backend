/**
 * ChatConfig - Gemini(Google GenAI SDK) 클라이언트 설정
 *
 * Spring AI 자동 설정이 만드는 Client 빈에는 타임아웃이 없고, SDK 기본값으로 503 등에서 최대 5번(약 30초) 재시도함.
 * 그 결과 질문 하나가 몇 분씩 응답 없이 걸리는 문제가 있었음.
 * 자동 설정의 Client 빈은 @ConditionalOnMissingBean이라, 같은 타입의 빈을 여기서 만들면 자동 설정 대신 이 빈이 쓰임.
 */
package com.example.inventory.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    // 메서드 이름을 자동 설정의 빈 이름(googleGenAiClient)과 맞춰서 무엇을 대체하는지 드러냄
    @Bean
    public Client googleGenAiClient(
            // 자동 설정을 대체하므로 API 키도 직접 넣어야 함 (키 위치는 그대로 application-local.yaml)
            @Value("${spring.ai.google.genai.api-key}") String apiKey,
            @Value("${chat.gemini.timeout-ms}") int timeoutMs,
            @Value("${chat.gemini.retry-attempts}") int retryAttempts) {

        HttpOptions httpOptions = HttpOptions.builder()
                .timeout(timeoutMs)     // 단위: 밀리초 (로컬 가짜 서버로 2000 → 약 2초에 포기하는 것 확인)
                .retryOptions(HttpRetryOptions.builder()
                        .attempts(retryAttempts)    // 첫 시도 포함 총 시도 횟수. 1이면 재시도 없음
                        .build())
                .build();

        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(httpOptions)
                .build();
    }
}

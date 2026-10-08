/**
 * Chat controller
 * Chat with AI
 */

// POST /api/chat, ADMIN+STAFF
// 지금은 요청 바디 { "message": "..." } → 응답 { "answer": "..." } 만.
// history, usedTools, sources는 다음 단계에서 추가
package com.example.inventory.controller;

import com.example.inventory.service.ChatService;
import com.example.inventory.dto.response.ChatResponse;
import com.example.inventory.dto.request.ChatRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;

@Tag(name = "채팅", description = "채팅 기능")
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // POST /api/chat — 200 OK
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "챗봇 채팅", description = "ADMIN 또는 STAFF 전용. 채팅 기능을 제공합니다.")
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String answer = chatService.chat(request);
        ChatResponse chatResponse = new ChatResponse(answer);
        return ResponseEntity.ok(chatResponse);
    }
}

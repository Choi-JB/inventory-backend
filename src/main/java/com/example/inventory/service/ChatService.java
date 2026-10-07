package com.example.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.ai.chat.client.ChatClient;
import com.example.inventory.tool.InventoryQueryTools;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder builder, InventoryQueryTools inventoryQueryTools) {
        this.chatClient = builder
            .defaultSystem("""
                너는 재고관리 시스템의 도우미야. 
                재고 수치는 주어진 데이터로만 답하고, 데이터가 없으면 '확인할 수 없습니다'라고 답해. 
                재고관리와 관계없는 질문에는 답하지 말고 할 수 있는 일을 안내해. 답변은 한국어로 2문장 이내.""")
            .defaultTools(inventoryQueryTools)
            .build();
    }

    public String chat(String message) {
        return this.chatClient.prompt()
            .user(message)
            .call()
            .content();
    }
}
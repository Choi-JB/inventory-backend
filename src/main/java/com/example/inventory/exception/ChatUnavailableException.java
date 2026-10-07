/**
 * Google AI 서버 문제
 */
package com.example.inventory.exception;

import org.springframework.http.HttpStatus;

public class ChatUnavailableException extends BusinessException{
    public ChatUnavailableException(String message) {
        super(message);
    }

    @Override
    public String getCode() {
        return "CHAT_UNAVAILABLE";
    }
    @Override
    public HttpStatus getStatus() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}

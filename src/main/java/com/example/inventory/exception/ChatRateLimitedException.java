/**
 * 무료 한도 초과
 */
package com.example.inventory.exception;

import org.springframework.http.HttpStatus;

public class ChatRateLimitedException extends BusinessException {
    public ChatRateLimitedException(String message) {
        super(message);
    }

    @Override
    public String getCode() {
        return "CHAT_RATE_LIMITED";
    }
    @Override
    public HttpStatus getStatus() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }
}

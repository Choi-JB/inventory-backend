package com.example.inventory.exception;

import org.springframework.http.HttpStatus;

/**
 * 같은 멱등성 키로 다른 내용의 거래가 이미 저장된 경우.
 */
public class IdempotencyConflictException extends BusinessException {

    public IdempotencyConflictException(String message) {
        super(message);
    }

    @Override
    public String getCode() {
        return "IDEMPOTENCY_CONFLICT";
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}

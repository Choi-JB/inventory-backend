package com.example.inventory.exception;

import org.springframework.http.HttpStatus;

/**
 * 토큰 검증 실패 시 예외
 * 401 status code
 */
public class InvalidTokenException extends BusinessException {
    public InvalidTokenException(String message) {
        super(message);
    }

    @Override
    public String getCode() {
        return "INVALID_TOKEN";
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNAUTHORIZED; //401 status code
    }
}

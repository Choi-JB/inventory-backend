/**
 * ValidationException - 유효성 검사 예외
 * "카테고리 하위 존재" 같은 비즈니스 규칙 검증 실패용
 */
package com.example.inventory.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends BusinessException {

    public ValidationException(String message) {
        super(message);
    }

    @Override
    public String getCode() {
        // API 명세서 에러코드 표에 있는 값 그대로
        return "VALIDATION_ERROR";
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
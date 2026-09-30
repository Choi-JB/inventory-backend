/**
 * RollbackConflictException - 롤백 충돌 예외 (재고 조정 충돌 예외)
 * 409 status code
 */
package com.example.inventory.exception;

import org.springframework.http.HttpStatus;

public class RollbackConflictException extends BusinessException {

    public RollbackConflictException(String message) {
        super(message);
    }

    @Override
    public String getCode() {
        return "ROLLBACK_CONFLICT";
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}

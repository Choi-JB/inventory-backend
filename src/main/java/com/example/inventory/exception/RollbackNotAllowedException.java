/**
 * RollbackNotAllowedException - 롤백 불가 예외
 * 409 status code
 */
package com.example.inventory.exception;

import org.springframework.http.HttpStatus;

public class RollbackNotAllowedException extends BusinessException {

    public RollbackNotAllowedException(String message) {
        super(message);
    }

    @Override
    public String getCode() {
        return "ROLLBACK_NOT_ALLOWED";
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
    
}

/**
 * Custom exception for delete conflict
 * 삭제 충돌 예외
 * 409 status code
 */
package com.example.inventory.exception;

import org.springframework.http.HttpStatus;

public class DeleteConflictException extends BusinessException {

    public DeleteConflictException(String message) {
        super(message);
    }

    @Override
    public String getCode() {
        return "DELETE_CONFLICT";
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
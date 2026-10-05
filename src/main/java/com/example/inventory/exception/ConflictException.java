/**
 * Custom exception for conflict
 * 409 status code
 */
package com.example.inventory.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends BusinessException {
    public ConflictException(String message) {
        super(message);
    }

    @Override
    public String getCode() {
        return "CONFLICT";
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}

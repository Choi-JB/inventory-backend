/**
 * Business exception 추상 클래스
 * 400, 404, 409 status code
 */
package com.example.inventory.exception;

import org.springframework.http.HttpStatus;

public abstract class BusinessException extends RuntimeException {

    protected BusinessException(String message) {
        super(message);
    }

    public abstract String getCode();
    public abstract HttpStatus getStatus();
}
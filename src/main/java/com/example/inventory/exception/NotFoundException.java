/**
 * Custom exception for resource not found 
 * 404 status code
 */
package com.example.inventory.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(message);
    }

    @Override
    public String getCode() {
        return "NOT_FOUND";
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
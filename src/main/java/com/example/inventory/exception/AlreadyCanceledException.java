package com.example.inventory.exception;

import org.springframework.http.HttpStatus;

public class AlreadyCanceledException extends BusinessException {

    public AlreadyCanceledException(String message) {
        super(message);
    }

    @Override
    public String getCode() {
        return "ALREADY_CANCELED";
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}

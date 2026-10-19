/**
 * InsufficientStockException - 재고 부족 예외
 * 409 status code
 */
package com.example.inventory.exception;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends BusinessException {

    public InsufficientStockException(String message) {
        super(message);
    }

    @Override
    public String getCode() {
        return "INSUFFICIENT_STOCK";
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}

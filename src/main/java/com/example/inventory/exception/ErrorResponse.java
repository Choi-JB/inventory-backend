/**
 * 공통 에러 응답 포맷: code, message, timestamp
 * 400, 404, 409 status code
 */
package com.example.inventory.exception;

import java.time.OffsetDateTime;

public record ErrorResponse(String code, String message, OffsetDateTime timestamp) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, OffsetDateTime.now());
    }
}
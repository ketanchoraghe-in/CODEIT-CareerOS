package com.codeit.careeros.exception;

import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
public class ErrorResponse {

    private final boolean success;
    private final String message;
    private final String errorCode;
    private final String timestamp;
    private final List<FieldErrorDetail> fieldErrors;

    public ErrorResponse(String message, String errorCode, List<FieldErrorDetail> fieldErrors) {
        this.success = false;
        this.message = message;
        this.errorCode = errorCode;
        this.timestamp = Instant.now().toString();
        this.fieldErrors = fieldErrors;
    }

    public record FieldErrorDetail(String field, String message) {
    }
}
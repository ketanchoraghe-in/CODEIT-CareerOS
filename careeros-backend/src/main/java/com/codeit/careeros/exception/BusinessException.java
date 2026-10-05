package com.codeit.careeros.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final int httpStatus;

    public BusinessException(ErrorCode errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message, 404);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message, 409);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message, 400);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(ErrorCode.UNAUTHORIZED, message, 401);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.FORBIDDEN, message, 403);
    }

    public static BusinessException gone(String message) {
        return new BusinessException(ErrorCode.ATTEMPT_EXPIRED, message, 410);
    }

    public static BusinessException serviceUnavailable(String message) {
        return new BusinessException(ErrorCode.AI_NOT_CONFIGURED, message, 503);
    }

    public static BusinessException aiError(String message) {
        return new BusinessException(ErrorCode.AI_ERROR, message, 502);
    }
}
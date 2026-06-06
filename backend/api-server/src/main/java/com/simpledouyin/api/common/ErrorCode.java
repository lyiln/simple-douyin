package com.simpledouyin.api.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_PARAMETER(40001, HttpStatus.BAD_REQUEST, "invalid parameter"),
    REQUIRED_VALUE_MISSING(40002, HttpStatus.BAD_REQUEST, "required value is missing"),
    CONTENT_TOO_LONG(40003, HttpStatus.BAD_REQUEST, "content is too long"),
    UNAUTHORIZED(40101, HttpStatus.UNAUTHORIZED, "unauthorized"),
    FORBIDDEN(40301, HttpStatus.FORBIDDEN, "forbidden"),
    VIDEO_NOT_FOUND(40401, HttpStatus.NOT_FOUND, "video not found"),
    USER_NOT_FOUND(40402, HttpStatus.NOT_FOUND, "user not found"),
    CONFLICT(40901, HttpStatus.CONFLICT, "conflict"),
    FILE_TOO_LARGE(41301, HttpStatus.PAYLOAD_TOO_LARGE, "file too large"),
    TOO_MANY_REQUESTS(42901, HttpStatus.TOO_MANY_REQUESTS, "too many requests"),
    INTERNAL_ERROR(50001, HttpStatus.INTERNAL_SERVER_ERROR, "internal server error");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(int code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String message() {
        return message;
    }
}

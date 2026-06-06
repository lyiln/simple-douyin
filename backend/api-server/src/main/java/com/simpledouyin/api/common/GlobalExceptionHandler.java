package com.simpledouyin.api.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.errorCode();
        request.setAttribute(RequestContext.ERROR_MESSAGE_ATTRIBUTE, exception.getMessage());
        return ResponseEntity
                .status(errorCode.httpStatus())
                .body(ApiResponse.failure(errorCode, exception.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_PARAMETER;
        request.setAttribute(RequestContext.ERROR_MESSAGE_ATTRIBUTE, exception.getMessage());
        return ResponseEntity
                .status(errorCode.httpStatus())
                .body(ApiResponse.failure(errorCode));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.FILE_TOO_LARGE;
        request.setAttribute(RequestContext.ERROR_MESSAGE_ATTRIBUTE, exception.getMessage());
        return ResponseEntity
                .status(errorCode.httpStatus())
                .body(ApiResponse.failure(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        request.setAttribute(RequestContext.ERROR_MESSAGE_ATTRIBUTE, exception.getMessage());
        log.error("Unhandled request exception", exception);
        return ResponseEntity
                .status(errorCode.httpStatus())
                .body(ApiResponse.failure(errorCode));
    }
}

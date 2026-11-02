package com.ecommerce.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex, HttpServletRequest request) {
        ErrorCode ec = ex.getErrorCode();
        return ResponseEntity.status(ec.getHttpStatus())
                .body(ErrorResponse.builder()
                        .code(ec.getCode())
                        .error(ec.name())
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ErrorCode ec = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(ec.getHttpStatus())
                .body(ErrorResponse.builder()
                        .code(ec.getCode())
                        .error(ec.name())
                        .message(ec.getMessage())
                        .path(request.getRequestURI())
                        .fieldErrors(fieldErrors)
                        .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        ErrorCode ec = ErrorCode.ACCESS_DENIED;
        return ResponseEntity.status(ec.getHttpStatus())
                .body(ErrorResponse.builder()
                        .code(ec.getCode())
                        .error(ec.name())
                        .message(ec.getMessage())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        ErrorCode ec = ErrorCode.BAD_CREDENTIALS;
        return ResponseEntity.status(ec.getHttpStatus())
                .body(ErrorResponse.builder()
                        .code(ec.getCode())
                        .error(ec.name())
                        .message(ec.getMessage())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        ErrorCode ec = ErrorCode.MALFORMED_JSON;
        return ResponseEntity.status(ec.getHttpStatus())
                .body(ErrorResponse.builder()
                        .code(ec.getCode())
                        .error(ec.name())
                        .message(ec.getMessage())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUncaughtException(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: ", request.getRequestURI(), ex);
        ErrorCode ec = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(ec.getHttpStatus())
                .body(ErrorResponse.builder()
                        .code(ec.getCode())
                        .error(ec.name())
                        .message(ec.getMessage())
                        .path(request.getRequestURI())
                        .build());
    }
}

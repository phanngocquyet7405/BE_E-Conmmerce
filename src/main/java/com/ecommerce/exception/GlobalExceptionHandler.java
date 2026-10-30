package com.ecommerce.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                        .code(4004)
                        .error("NOT_FOUND")
                        .message("Tài nguyên hoặc đường dẫn yêu cầu không tồn tại: " + request.getRequestURI())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String msg = "Giá trị tham số '" + ex.getName() + "' không đúng định dạng yêu cầu";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .code(ErrorCode.VALIDATION_ERROR.getCode())
                        .error("TYPE_MISMATCH")
                        .message(msg)
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSizeException(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        ErrorCode ec = ErrorCode.FILE_SIZE_EXCEEDED;
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.builder()
                        .code(ec.getCode())
                        .error(ec.name())
                        .message(ec.getMessage())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKey(
            DuplicateKeyException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.builder()
                        .code(4009)
                        .error("DUPLICATE_KEY")
                        .message("Dữ liệu bị trùng lặp khóa duy nhất (unique key constraint violation)")
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(ErrorResponse.builder()
                        .code(ex.getStatusCode().value())
                        .error(ex.getStatusCode().toString())
                        .message(ex.getReason())
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
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build());
    }
}

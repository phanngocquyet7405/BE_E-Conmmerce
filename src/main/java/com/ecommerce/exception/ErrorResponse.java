package com.ecommerce.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    @Builder.Default
    private final boolean success = false;
    private final int code;
    private final String error;
    private final String message;
    private final String path;
    private final Map<String, String> fieldErrors;
    @Builder.Default
    private final Instant timestamp = Instant.now();
}

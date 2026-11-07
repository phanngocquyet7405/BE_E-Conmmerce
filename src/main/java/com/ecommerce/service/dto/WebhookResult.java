package com.ecommerce.service.dto;

import org.springframework.http.HttpStatus;

public record WebhookResult(
        HttpStatus status,
        boolean success,
        String message
) {
    public static WebhookResult ok() {
        return new WebhookResult(HttpStatus.OK, true, "OK");
    }

    public static WebhookResult unauthorized(String message) {
        return new WebhookResult(HttpStatus.UNAUTHORIZED, false, message);
    }

    public static WebhookResult badRequest(String message) {
        return new WebhookResult(HttpStatus.BAD_REQUEST, false, message);
    }

    public static WebhookResult internalError(String message) {
        return new WebhookResult(HttpStatus.INTERNAL_SERVER_ERROR, false, message);
    }
}

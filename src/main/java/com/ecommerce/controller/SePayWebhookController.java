package com.ecommerce.controller;

import com.ecommerce.service.SePayWebhookService;
import com.ecommerce.service.dto.WebhookResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class SePayWebhookController {

    private final SePayWebhookService sePayWebhookService;

    /**
     * Endpoint tiếp nhận Webhook từ SePay VietQR:
     * - Hỗ trợ xác thực qua Authorization: Apikey <key>
     * - Hỗ trợ xác thực qua HMAC-SHA256 (X-SePay-Signature và X-SePay-Timestamp)
     * - Delegate toàn bộ xử lý nghiệp vụ sang SePayWebhookService
     */
    @PostMapping("/sepay-webhook")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-SePay-Signature", required = false) String signature,
            @RequestHeader(value = "X-SePay-Timestamp", required = false) String timestampHeader) {

        WebhookResult result = sePayWebhookService.processWebhook(rawBody, authorization, signature, timestampHeader);

        if (result.success()) {
            return ResponseEntity.status(result.status()).body(Map.of("success", true));
        } else {
            return ResponseEntity.status(result.status())
                    .body(Map.of("success", false, "message", result.message()));
        }
    }
}

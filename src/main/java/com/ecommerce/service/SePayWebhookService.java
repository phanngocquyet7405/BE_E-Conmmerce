package com.ecommerce.service;

import com.ecommerce.service.dto.WebhookResult;

public interface SePayWebhookService {

    /**
     * Xác thực và xử lý Webhook nhận từ SePay:
     * - Hỗ trợ xác thực qua Authorization: Apikey <key>
     * - Hỗ trợ xác thực qua HMAC-SHA256 (X-SePay-Signature & X-SePay-Timestamp)
     * - Quản lý Idempotency và trạng thái giao dịch (RECEIVED/PROCESSED/FAILED/MISMATCH/IGNORED)
     * - Gạch nợ đơn hàng nguyên tử khi nhận đủ tiền
     *
     * @param rawBody       Nội dung thô JSON nhận từ request
     * @param authorization Header Authorization (Apikey ...)
     * @param signature     Header X-SePay-Signature (sha256=...)
     * @param timestamp     Header X-SePay-Timestamp (epoch seconds)
     * @return WebhookResult chứa HTTP status và kết quả xử lý
     */
    WebhookResult processWebhook(String rawBody, String authorization, String signature, String timestamp);
}

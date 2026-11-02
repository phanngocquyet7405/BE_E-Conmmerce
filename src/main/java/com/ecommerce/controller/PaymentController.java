package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.dto.request.SePayWebhookRequest;
import com.ecommerce.dto.response.PaymentQrResponse;
import com.ecommerce.dto.response.PaymentStatusResponse;
import com.ecommerce.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiRoutes.Payment.BASE)
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // 1. MOBILE/FE: Lấy mã VietQR SePay & thông tin chuyển khoản cho đơn hàng
    @GetMapping(ApiRoutes.Payment.SEPAY_QR_INFO)
    public ResponseEntity<ApiResponse<PaymentQrResponse>> getPaymentQrInfo(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin thanh toán thành công",
                paymentService.getPaymentQrInfo(orderId)));
    }

    // 2. MOBILE/FE: Kiểm tra trạng thái thanh toán đơn hàng (dùng để Auto-poll sau khi khách chuyển tiền)
    @GetMapping(ApiRoutes.Payment.STATUS)
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> checkPaymentStatus(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.checkPaymentStatus(orderId)));
    }

    // 3. SEPAY WEBHOOK: Tự động tiếp nhận thông báo chuyển khoản thành công từ SePay
    @PostMapping(ApiRoutes.Payment.SEPAY_WEBHOOK)
    public ResponseEntity<ApiResponse<Void>> sepayWebhook(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody SePayWebhookRequest request) {
        paymentService.processSepayWebhook(authHeader, request);
        return ResponseEntity.ok(ApiResponse.ok("Xử lý Webhook SePay thành công", null));
    }
}

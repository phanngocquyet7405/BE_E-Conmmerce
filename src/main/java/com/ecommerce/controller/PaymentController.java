package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.dto.response.PaymentQrResponse;
import com.ecommerce.dto.response.PaymentStatusResponse;
import com.ecommerce.security.CustomUserDetails;
import com.ecommerce.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiRoutes.Payment.BASE)
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // 1. MOBILE/FE: Lấy mã VietQR SePay & thông tin chuyển khoản cho đơn hàng (Bảo vệ IDOR)
    @GetMapping(ApiRoutes.Payment.SEPAY_QR_INFO)
    public ResponseEntity<ApiResponse<PaymentQrResponse>> getPaymentQrInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String orderId) {
        boolean isAdmin = userDetails != null && userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        String userId = userDetails != null ? userDetails.getId() : null;

        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin thanh toán thành công",
                paymentService.getPaymentQrInfo(userId, isAdmin, orderId)));
    }

    // 2. MOBILE/FE: Kiểm tra trạng thái thanh toán đơn hàng (Bảo vệ IDOR)
    @GetMapping(ApiRoutes.Payment.STATUS)
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> checkPaymentStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String orderId) {
        boolean isAdmin = userDetails != null && userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        String userId = userDetails != null ? userDetails.getId() : null;

        return ResponseEntity.ok(ApiResponse.ok(
                paymentService.checkPaymentStatus(userId, isAdmin, orderId)));
    }
}

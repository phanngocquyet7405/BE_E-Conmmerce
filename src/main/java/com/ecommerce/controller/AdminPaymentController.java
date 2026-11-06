package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.common.response.PageResponse;
import com.ecommerce.document.SePayTransactionStatus;
import com.ecommerce.dto.request.AdminProcessTransactionRequest;
import com.ecommerce.dto.response.SePayTransactionResponse;
import com.ecommerce.security.CustomUserDetails;
import com.ecommerce.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiRoutes.API_V1 + "/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final PaymentService paymentService;

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<SePayTransactionResponse>>> getTransactions(
            @RequestParam(required = false) SePayTransactionStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách giao dịch SePay thành công",
                paymentService.getTransactionsForAdmin(status, pageable)));
    }

    @PatchMapping("/transactions/{id}/process")
    public ResponseEntity<ApiResponse<SePayTransactionResponse>> processTransaction(
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            @PathVariable String id,
            @Valid @RequestBody AdminProcessTransactionRequest request) {
        String adminEmail = adminDetails != null ? adminDetails.getUsername() : "ADMIN";
        return ResponseEntity.ok(ApiResponse.ok("Xử lý giao dịch SePay thành công",
                paymentService.processTransactionForAdmin(adminEmail, id, request)));
    }
}

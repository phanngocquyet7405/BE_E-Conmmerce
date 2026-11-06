package com.ecommerce.service;

import com.ecommerce.common.response.PageResponse;
import com.ecommerce.document.SePayTransactionStatus;
import com.ecommerce.dto.request.AdminProcessTransactionRequest;
import com.ecommerce.dto.response.PaymentMethodResponse;
import com.ecommerce.dto.response.PaymentQrResponse;
import com.ecommerce.dto.response.PaymentStatusResponse;
import com.ecommerce.dto.response.SePayTransactionResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PaymentService {
    PaymentQrResponse getPaymentQrInfo(String userId, boolean isAdmin, String orderId);
    PaymentStatusResponse checkPaymentStatus(String userId, boolean isAdmin, String orderId);
    List<PaymentMethodResponse> getAvailablePaymentMethods();
    PageResponse<SePayTransactionResponse> getTransactionsForAdmin(SePayTransactionStatus status, Pageable pageable);
    SePayTransactionResponse processTransactionForAdmin(String adminEmail, String transactionId, AdminProcessTransactionRequest request);
}

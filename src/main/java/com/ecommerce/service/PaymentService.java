package com.ecommerce.service;

import com.ecommerce.dto.response.PaymentMethodResponse;
import com.ecommerce.dto.response.PaymentQrResponse;
import com.ecommerce.dto.response.PaymentStatusResponse;

import java.util.List;

public interface PaymentService {
    PaymentQrResponse getPaymentQrInfo(String userId, boolean isAdmin, String orderId);
    PaymentStatusResponse checkPaymentStatus(String userId, boolean isAdmin, String orderId);
    List<PaymentMethodResponse> getAvailablePaymentMethods();
}

package com.ecommerce.service;

import com.ecommerce.dto.response.PaymentQrResponse;
import com.ecommerce.dto.response.PaymentStatusResponse;

public interface PaymentService {
    PaymentQrResponse getPaymentQrInfo(String orderId);
    PaymentStatusResponse checkPaymentStatus(String orderId);
}

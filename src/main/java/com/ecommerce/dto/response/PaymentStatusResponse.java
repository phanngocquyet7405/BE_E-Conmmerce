package com.ecommerce.dto.response;

import com.ecommerce.document.OrderStatus;
import com.ecommerce.document.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class PaymentStatusResponse {
    private final String orderId;
    private final String orderCode;
    private final PaymentStatus paymentStatus;
    private final OrderStatus orderStatus;
    private final Instant paidAt;
    private final String sepayTransactionId;
    private final BigDecimal finalAmount;
    private final BigDecimal paidAmount;
    private final BigDecimal amountDiscrepancy;
    private final Boolean isOverpaid;
    private final Boolean isUnderpaid;
}

package com.ecommerce.dto.response;

import com.ecommerce.document.PaymentMethod;
import com.ecommerce.document.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PaymentQrResponse {
    private final String orderId;
    private final String orderCode;
    private final BigDecimal amount;
    private final PaymentMethod paymentMethod;
    private final PaymentStatus paymentStatus;
    private final String bankName;
    private final String accountNumber;
    private final String accountHolder;
    private final String transferContent;
    private final String qrImageUrl;
    private final String paymentInstruction;
}

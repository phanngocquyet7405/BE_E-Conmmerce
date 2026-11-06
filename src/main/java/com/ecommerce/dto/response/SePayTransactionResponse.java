package com.ecommerce.dto.response;

import com.ecommerce.document.SePayTransaction;
import com.ecommerce.document.SePayTransactionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SePayTransactionResponse {
    private final String id;
    private final Long sepayId;
    private final String gateway;
    private final LocalDateTime transactionDate;
    private final String accountNumber;
    private final String subAccount;
    private final String code;
    private final Long amountIn;
    private final Long amountOut;
    private final Long accumulated;
    private final String content;
    private final String referenceCode;
    private final SePayTransactionStatus status;
    private final String orderId;
    private final String orderCode;
    private final Long amountDiscrepancy;
    private final String failureReason;
    private final Boolean adminProcessed;
    private final String processedBy;
    private final LocalDateTime processedAt;
    private final String adminNote;
    private final LocalDateTime createdAt;

    public static SePayTransactionResponse fromEntity(SePayTransaction tx) {
        if (tx == null) return null;
        return SePayTransactionResponse.builder()
                .id(tx.getId())
                .sepayId(tx.getSepayId())
                .gateway(tx.getGateway())
                .transactionDate(tx.getTransactionDate())
                .accountNumber(tx.getAccountNumber())
                .subAccount(tx.getSubAccount())
                .code(tx.getCode())
                .amountIn(tx.getAmountIn())
                .amountOut(tx.getAmountOut())
                .accumulated(tx.getAccumulated())
                .content(tx.getContent())
                .referenceCode(tx.getReferenceCode())
                .status(tx.getStatus())
                .orderId(tx.getOrderId())
                .orderCode(tx.getOrderCode())
                .amountDiscrepancy(tx.getAmountDiscrepancy())
                .failureReason(tx.getFailureReason())
                .adminProcessed(Boolean.TRUE.equals(tx.getAdminProcessed()))
                .processedBy(tx.getProcessedBy())
                .processedAt(tx.getProcessedAt())
                .adminNote(tx.getAdminNote())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}

package com.ecommerce.dto.request;

import com.ecommerce.document.OrderStatus;
import com.ecommerce.document.PaymentMethod;
import com.ecommerce.document.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminOrderCriteria {
    private String keyword;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;
    private Instant from;
    private Instant to;
    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 15;
    @Builder.Default
    private String sortBy = "createdAt";
    @Builder.Default
    private String sortDir = "desc";
}

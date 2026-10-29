package com.ecommerce.dto.response;

import com.ecommerce.document.Address;
import com.ecommerce.document.OrderItem;
import com.ecommerce.document.OrderStatus;
import com.ecommerce.document.PaymentMethod;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class OrderResponse {
    private final String id;
    private final String orderCode;
    private final String userId;
    private final String customerName;
    private final String customerEmail;
    private final String customerPhone;
    private final Address shippingAddress;
    private final List<OrderItem> items;
    private final BigDecimal totalAmount;
    private final BigDecimal shippingFee;
    private final BigDecimal finalAmount;
    private final PaymentMethod paymentMethod;
    private final OrderStatus status;
    private final String note;
    private final Instant createdAt;
}

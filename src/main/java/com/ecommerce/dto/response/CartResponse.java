package com.ecommerce.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class CartResponse {
    private final String id;
    private final String userId;
    private final List<CartItemResponse> items;
    private final int totalItems;
    private final BigDecimal totalAmount;
}

package com.ecommerce.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CartItemResponse {
    private final String productId;
    private final String sku;
    private final String productName;
    private final String thumbnailUrl;
    private final BigDecimal unitPrice;
    private final Integer quantity;
    private final BigDecimal subTotal;
    private final String categoryName;
    private final String brandName;
}

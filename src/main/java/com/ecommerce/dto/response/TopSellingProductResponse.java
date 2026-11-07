package com.ecommerce.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopSellingProductResponse {
    private String productId;
    private String productName;
    private long totalSold;
    private BigDecimal totalRevenue;
}

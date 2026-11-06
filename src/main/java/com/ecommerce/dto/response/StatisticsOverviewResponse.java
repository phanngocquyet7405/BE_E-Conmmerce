package com.ecommerce.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsOverviewResponse {
    private BigDecimal totalRevenue;
    private long totalOrders;
    private long paidOrdersCount;
    private BigDecimal averageOrderValue;
    private Map<String, Long> orderCountByStatus;
    private long newUsersCount;
    private long lowStockProductsCount;
}

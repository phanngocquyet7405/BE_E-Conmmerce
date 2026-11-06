package com.ecommerce.service;

import com.ecommerce.common.response.PageResponse;
import com.ecommerce.dto.response.LowStockProductResponse;
import com.ecommerce.dto.response.RevenueStatisticsResponse;
import com.ecommerce.dto.response.StatisticsOverviewResponse;
import com.ecommerce.dto.response.TopSellingProductResponse;

import java.time.Instant;
import java.util.List;

public interface StatisticsService {
    StatisticsOverviewResponse getOverview(Instant from, Instant to);
    List<RevenueStatisticsResponse> getRevenueAnalytics(String groupBy, Instant from, Instant to);
    List<TopSellingProductResponse> getTopSellingProducts(int limit, Instant from, Instant to);
    PageResponse<LowStockProductResponse> getLowStockProducts(int threshold, int page, int size);
}

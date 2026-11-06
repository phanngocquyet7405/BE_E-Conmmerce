package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.common.response.PageResponse;
import com.ecommerce.dto.response.LowStockProductResponse;
import com.ecommerce.dto.response.RevenueStatisticsResponse;
import com.ecommerce.dto.response.StatisticsOverviewResponse;
import com.ecommerce.dto.response.TopSellingProductResponse;
import com.ecommerce.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping(ApiRoutes.Statistics.ADMIN_BASE)
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatisticsController {

    private final StatisticsService statisticsService;

    // 1. ADMIN: Tổng quan kinh doanh (Doanh thu D9, số đơn theo trạng thái, users mới, cảnh báo tồn kho)
    @GetMapping(ApiRoutes.Statistics.OVERVIEW)
    public ResponseEntity<ApiResponse<StatisticsOverviewResponse>> getOverview(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy dữ liệu tổng quan thành công",
                statisticsService.getOverview(from, to)));
    }

    // 2. ADMIN: Biểu đồ doanh thu theo ngày hoặc tháng (Theo D9: PAID & !CANCELLED)
    @GetMapping(ApiRoutes.Statistics.REVENUE)
    public ResponseEntity<ApiResponse<List<RevenueStatisticsResponse>>> getRevenueAnalytics(
            @RequestParam(defaultValue = "day") String groupBy,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy thống kê doanh thu thành công",
                statisticsService.getRevenueAnalytics(groupBy, from, to)));
    }

    // 3. ADMIN: Danh sách sản phẩm bán chạy nhất
    @GetMapping(ApiRoutes.Statistics.TOP_PRODUCTS)
    public ResponseEntity<ApiResponse<List<TopSellingProductResponse>>> getTopSellingProducts(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy top sản phẩm bán chạy thành công",
                statisticsService.getTopSellingProducts(limit, from, to)));
    }

    // 4. ADMIN: Cảnh báo danh sách sản phẩm tồn kho thấp
    @GetMapping(ApiRoutes.Statistics.LOW_STOCK)
    public ResponseEntity<ApiResponse<PageResponse<LowStockProductResponse>>> getLowStockProducts(
            @RequestParam(defaultValue = "10") int threshold,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách sản phẩm tồn kho thấp thành công",
                statisticsService.getLowStockProducts(threshold, page, size)));
    }
}

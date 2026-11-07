package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.common.response.PageResponse;
import com.ecommerce.dto.response.LowStockProductResponse;
import com.ecommerce.dto.response.RevenueStatisticsResponse;
import com.ecommerce.dto.response.StatisticsOverviewResponse;
import com.ecommerce.dto.response.TopSellingProductResponse;
import com.ecommerce.exception.GlobalExceptionHandler;
import com.ecommerce.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminStatisticsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StatisticsService statisticsService;

    @InjectMocks
    private AdminStatisticsController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/admin/statistics/overview trả 200 kèm thống kê tổng quan")
    void getOverview_Returns200() throws Exception {
        StatisticsOverviewResponse mockOverview = StatisticsOverviewResponse.builder()
                .totalRevenue(new BigDecimal("1000000"))
                .totalOrders(10)
                .paidOrdersCount(8)
                .averageOrderValue(new BigDecimal("125000"))
                .orderCountByStatus(Map.of("DELIVERED", 8L, "PENDING", 2L))
                .newUsersCount(15)
                .lowStockProductsCount(2)
                .build();

        when(statisticsService.getOverview(any(), any())).thenReturn(mockOverview);

        mockMvc.perform(get(ApiRoutes.Statistics.ADMIN_BASE + ApiRoutes.Statistics.OVERVIEW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.totalRevenue").value(1000000))
                .andExpect(jsonPath("$.data.paidOrdersCount").value(8))
                .andExpect(jsonPath("$.data.averageOrderValue").value(125000));
    }

    @Test
    @DisplayName("GET /api/v1/admin/statistics/revenue trả 200 kèm danh sách doanh thu")
    void getRevenue_Returns200() throws Exception {
        RevenueStatisticsResponse rev = RevenueStatisticsResponse.builder()
                .period("2026-11-06")
                .revenue(new BigDecimal("500000"))
                .orderCount(5)
                .build();

        when(statisticsService.getRevenueAnalytics(eq("day"), any(), any()))
                .thenReturn(List.of(rev));

        mockMvc.perform(get(ApiRoutes.Statistics.ADMIN_BASE + ApiRoutes.Statistics.REVENUE)
                        .param("groupBy", "day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data[0].period").value("2026-11-06"))
                .andExpect(jsonPath("$.data[0].revenue").value(500000))
                .andExpect(jsonPath("$.data[0].orderCount").value(5));
    }

    @Test
    @DisplayName("GET /api/v1/admin/statistics/top-products trả 200 kèm danh sách sản phẩm bán chạy")
    void getTopProducts_Returns200() throws Exception {
        TopSellingProductResponse top = TopSellingProductResponse.builder()
                .productId("p-1")
                .productName("Sản phẩm hot")
                .totalSold(50)
                .totalRevenue(new BigDecimal("2500000"))
                .build();

        when(statisticsService.getTopSellingProducts(eq(10), any(), any()))
                .thenReturn(List.of(top));

        mockMvc.perform(get(ApiRoutes.Statistics.ADMIN_BASE + ApiRoutes.Statistics.TOP_PRODUCTS)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data[0].productId").value("p-1"))
                .andExpect(jsonPath("$.data[0].totalSold").value(50));
    }

    @Test
    @DisplayName("GET /api/v1/admin/statistics/low-stock trả 200 kèm phân trang tồn kho")
    void getLowStock_Returns200() throws Exception {
        LowStockProductResponse item = LowStockProductResponse.builder()
                .id("p-low")
                .name("Hàng sắp hết")
                .sku("SKU-LOW-1")
                .stockQuantity(2)
                .price(new BigDecimal("99000"))
                .active(true)
                .build();

        PageResponse<LowStockProductResponse> page = PageResponse.<LowStockProductResponse>builder()
                .items(List.of(item))
                .pageNo(0)
                .pageSize(15)
                .totalElements(1)
                .totalPages(1)
                .last(true)
                .build();

        when(statisticsService.getLowStockProducts(eq(10), eq(0), eq(15))).thenReturn(page);

        mockMvc.perform(get(ApiRoutes.Statistics.ADMIN_BASE + ApiRoutes.Statistics.LOW_STOCK)
                        .param("threshold", "10")
                        .param("page", "0")
                        .param("size", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.items[0].id").value("p-low"))
                .andExpect(jsonPath("$.data.items[0].stockQuantity").value(2));
    }
}

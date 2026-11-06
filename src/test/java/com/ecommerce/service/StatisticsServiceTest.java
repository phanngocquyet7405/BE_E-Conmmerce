package com.ecommerce.service;

import com.ecommerce.common.response.PageResponse;
import com.ecommerce.document.*;
import com.ecommerce.dto.response.LowStockProductResponse;
import com.ecommerce.dto.response.RevenueStatisticsResponse;
import com.ecommerce.dto.response.StatisticsOverviewResponse;
import com.ecommerce.dto.response.TopSellingProductResponse;
import com.ecommerce.service.impl.StatisticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private StatisticsServiceImpl statisticsService;

    private Instant testTime;
    private Order paidOrder1;
    private Order paidOrder2;

    @BeforeEach
    void setUp() {
        testTime = Instant.parse("2026-11-06T10:00:00Z");

        OrderItem item1 = OrderItem.builder()
                .productId("p1")
                .productName("Laptop Asus")
                .quantity(2)
                .price(new BigDecimal("250000"))
                .subTotal(new BigDecimal("500000"))
                .build();

        OrderItem item2 = OrderItem.builder()
                .productId("p2")
                .productName("Chuột không dây")
                .quantity(3)
                .price(new BigDecimal("100000"))
                .subTotal(new BigDecimal("300000"))
                .build();

        paidOrder1 = Order.builder()
                .id("ord-1")
                .orderCode("BETES00000001")
                .status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .finalAmount(new BigDecimal("500000"))
                .createdAt(testTime)
                .items(List.of(item1))
                .build();

        paidOrder2 = Order.builder()
                .id("ord-2")
                .orderCode("BETES00000002")
                .status(OrderStatus.DELIVERED)
                .paymentStatus(PaymentStatus.PAID)
                .finalAmount(new BigDecimal("300000"))
                .createdAt(testTime)
                .items(List.of(item2))
                .build();
    }

    @Test
    @DisplayName("D9: getOverview tính doanh thu chỉ từ đơn có paymentStatus=PAID và status!=CANCELLED")
    void getOverview_CalculatesRevenueStrictlyAccordingToD9() {
        // Mock query tất cả đơn
        when(mongoTemplate.find(any(Query.class), eq(Order.class)))
                .thenReturn(List.of(paidOrder1, paidOrder2)) // query 1: all orders
                .thenReturn(List.of(paidOrder1, paidOrder2)); // query 2: paid non-cancelled orders

        when(mongoTemplate.count(any(Query.class), eq(User.class))).thenReturn(5L);
        when(mongoTemplate.count(any(Query.class), eq(Product.class))).thenReturn(3L);

        StatisticsOverviewResponse response = statisticsService.getOverview(null, null);

        assertNotNull(response);
        assertEquals(new BigDecimal("800000"), response.getTotalRevenue(), "Doanh thu = 500k + 300k = 800k");
        assertEquals(2, response.getTotalOrders());
        assertEquals(2, response.getPaidOrdersCount());
        assertEquals(new BigDecimal("400000"), response.getAverageOrderValue(), "AOV = 800k / 2 = 400k");
        assertEquals(1L, response.getOrderCountByStatus().get("CONFIRMED"));
        assertEquals(1L, response.getOrderCountByStatus().get("DELIVERED"));
        assertEquals(5L, response.getNewUsersCount());
        assertEquals(3L, response.getLowStockProductsCount());
    }

    @Test
    @DisplayName("getRevenueAnalytics phân nhóm doanh thu theo ngày và tháng chuẩn D9")
    void getRevenueAnalytics_GroupsByDayCorrectly() {
        when(mongoTemplate.find(any(Query.class), eq(Order.class)))
                .thenReturn(List.of(paidOrder1, paidOrder2));

        List<RevenueStatisticsResponse> stats = statisticsService.getRevenueAnalytics("day", null, null);

        assertNotNull(stats);
        assertEquals(1, stats.size(), "Cùng 1 ngày 2026-11-06 gom lại thành 1 nhóm");
        assertEquals("2026-11-06", stats.get(0).getPeriod());
        assertEquals(new BigDecimal("800000"), stats.get(0).getRevenue());
        assertEquals(2, stats.get(0).getOrderCount());
    }

    @Test
    @DisplayName("getTopSellingProducts tổng hợp số lượng và doanh thu theo sản phẩm")
    void getTopSellingProducts_AggregatesAndSortsCorrectly() {
        when(mongoTemplate.find(any(Query.class), eq(Order.class)))
                .thenReturn(List.of(paidOrder1, paidOrder2));

        List<TopSellingProductResponse> top = statisticsService.getTopSellingProducts(10, null, null);

        assertNotNull(top);
        assertEquals(2, top.size());
        // p2 bán được 3 cái, p1 bán được 2 cái -> p2 đứng đầu về số lượng
        assertEquals("p2", top.get(0).getProductId());
        assertEquals(3L, top.get(0).getTotalSold());
        assertEquals(new BigDecimal("300000"), top.get(0).getTotalRevenue());

        assertEquals("p1", top.get(1).getProductId());
        assertEquals(2L, top.get(1).getTotalSold());
        assertEquals(new BigDecimal("500000"), top.get(1).getTotalRevenue());
    }

    @Test
    @DisplayName("getLowStockProducts trả về danh sách sản phẩm dưới ngưỡng tồn kho")
    void getLowStockProducts_ReturnsLowStockItems() {
        Product p = Product.builder()
                .id("prod-low")
                .name("Tai nghe Bluetooth")
                .sku("HEADPHONE-01")
                .stockQuantity(3)
                .price(new BigDecimal("150000"))
                .active(true)
                .build();

        when(mongoTemplate.count(any(Query.class), eq(Product.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(List.of(p));

        PageResponse<LowStockProductResponse> page = statisticsService.getLowStockProducts(5, 0, 10);

        assertNotNull(page);
        assertEquals(1, page.getItems().size());
        assertEquals("prod-low", page.getItems().get(0).getId());
        assertEquals(3, page.getItems().get(0).getStockQuantity());
    }
}

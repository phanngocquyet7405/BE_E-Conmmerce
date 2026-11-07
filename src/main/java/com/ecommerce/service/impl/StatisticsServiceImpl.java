package com.ecommerce.service.impl;

import com.ecommerce.common.response.PageResponse;
import com.ecommerce.document.*;
import com.ecommerce.dto.response.*;
import com.ecommerce.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final MongoTemplate mongoTemplate;

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    @Override
    public StatisticsOverviewResponse getOverview(Instant from, Instant to) {
        // 1. Tiêu chí cho toàn bộ đơn hàng trong kỳ
        Criteria orderCriteria = buildDateRangeCriteria("createdAt", from, to);
        List<Order> allOrders = mongoTemplate.find(new Query(orderCriteria), Order.class);

        // 2. Doanh thu theo D9: Đơn có paymentStatus == PAID và status != CANCELLED
        Criteria paidCriteria = Criteria.where("paymentStatus").is(PaymentStatus.PAID)
                .and("status").ne(OrderStatus.CANCELLED);
        appendDateRange(paidCriteria, "createdAt", from, to);

        List<Order> paidOrders = mongoTemplate.find(new Query(paidCriteria), Order.class);

        BigDecimal totalRevenue = paidOrders.stream()
                .map(Order::getFinalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long paidOrdersCount = paidOrders.size();
        BigDecimal averageOrderValue = paidOrdersCount > 0
                ? totalRevenue.divide(BigDecimal.valueOf(paidOrdersCount), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 3. Đếm đơn theo trạng thái
        Map<String, Long> orderCountByStatus = new LinkedHashMap<>();
        for (OrderStatus st : OrderStatus.values()) {
            orderCountByStatus.put(st.name(), 0L);
        }
        for (Order o : allOrders) {
            if (o.getStatus() != null) {
                orderCountByStatus.put(o.getStatus().name(), orderCountByStatus.getOrDefault(o.getStatus().name(), 0L) + 1);
            }
        }

        // 4. Số người dùng mới đăng ký
        Criteria userCriteria = buildDateRangeCriteria("createdAt", from, to);
        long newUsersCount = mongoTemplate.count(new Query(userCriteria), User.class);

        // 5. Số sản phẩm sắp hết hàng (tồn kho <= 10 và đang active)
        Criteria lowStockCriteria = Criteria.where("stockQuantity").lte(10).and("active").is(true);
        long lowStockProductsCount = mongoTemplate.count(new Query(lowStockCriteria), Product.class);

        return StatisticsOverviewResponse.builder()
                .totalRevenue(totalRevenue)
                .totalOrders(allOrders.size())
                .paidOrdersCount(paidOrdersCount)
                .averageOrderValue(averageOrderValue)
                .orderCountByStatus(orderCountByStatus)
                .newUsersCount(newUsersCount)
                .lowStockProductsCount(lowStockProductsCount)
                .build();
    }

    @Override
    public List<RevenueStatisticsResponse> getRevenueAnalytics(String groupBy, Instant from, Instant to) {
        // D9: Doanh thu tính trên đơn có paymentStatus == PAID và status != CANCELLED
        Criteria criteria = Criteria.where("paymentStatus").is(PaymentStatus.PAID)
                .and("status").ne(OrderStatus.CANCELLED);
        appendDateRange(criteria, "createdAt", from, to);

        List<Order> paidOrders = mongoTemplate.find(new Query(criteria), Order.class);

        DateTimeFormatter formatter = "month".equalsIgnoreCase(groupBy) ? MONTH_FORMATTER : DAY_FORMATTER;

        Map<String, List<Order>> grouped = paidOrders.stream()
                .filter(o -> o.getCreatedAt() != null)
                .collect(Collectors.groupingBy(o -> formatter.format(o.getCreatedAt())));

        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    String period = entry.getKey();
                    List<Order> orders = entry.getValue();
                    BigDecimal revenue = orders.stream()
                            .map(Order::getFinalAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return RevenueStatisticsResponse.builder()
                            .period(period)
                            .revenue(revenue)
                            .orderCount(orders.size())
                            .build();
                })
                .toList();
    }

    @Override
    public List<TopSellingProductResponse> getTopSellingProducts(int limit, Instant from, Instant to) {
        int sanitizedLimit = Math.min(Math.max(limit, 1), 50);

        // D9: Chỉ lấy sản phẩm từ đơn hàng đã thanh toán và chưa bị hủy
        Criteria criteria = Criteria.where("paymentStatus").is(PaymentStatus.PAID)
                .and("status").ne(OrderStatus.CANCELLED);
        appendDateRange(criteria, "createdAt", from, to);

        List<Order> paidOrders = mongoTemplate.find(new Query(criteria), Order.class);

        // Gộp sản phẩm từ tất cả order items
        Map<String, AggregatedProduct> productMap = new HashMap<>();

        for (Order order : paidOrders) {
            if (order.getItems() == null) continue;
            for (OrderItem item : order.getItems()) {
                if (item.getProductId() == null) continue;
                String prodId = item.getProductId();
                AggregatedProduct agg = productMap.computeIfAbsent(prodId, id -> new AggregatedProduct(id, item.getProductName()));
                long qty = item.getQuantity() != null ? item.getQuantity() : 0L;
                BigDecimal subTotal = item.getSubTotal() != null
                        ? item.getSubTotal()
                        : (item.getPrice() != null ? item.getPrice().multiply(BigDecimal.valueOf(qty)) : BigDecimal.ZERO);

                agg.addSales(qty, subTotal);
            }
        }

        return productMap.values().stream()
                .sorted(Comparator.comparingLong(AggregatedProduct::getTotalSold).reversed()
                        .thenComparing(AggregatedProduct::getTotalRevenue, Comparator.reverseOrder()))
                .limit(sanitizedLimit)
                .map(agg -> TopSellingProductResponse.builder()
                        .productId(agg.getProductId())
                        .productName(agg.getProductName())
                        .totalSold(agg.getTotalSold())
                        .totalRevenue(agg.getTotalRevenue())
                        .build())
                .toList();
    }

    @Override
    public PageResponse<LowStockProductResponse> getLowStockProducts(int threshold, int page, int size) {
        int sanitizedThreshold = Math.max(threshold, 0);
        int sanitizedPage = Math.max(page, 0);
        int sanitizedSize = Math.min(Math.max(size, 1), 50);

        Query query = Query.query(Criteria.where("stockQuantity").lte(sanitizedThreshold));
        long total = mongoTemplate.count(query, Product.class);

        query.with(PageRequest.of(sanitizedPage, sanitizedSize, Sort.by(Sort.Direction.ASC, "stockQuantity")));
        List<Product> products = mongoTemplate.find(query, Product.class);

        List<LowStockProductResponse> items = products.stream()
                .map(p -> LowStockProductResponse.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .sku(p.getSku())
                        .stockQuantity(p.getStockQuantity())
                        .price(p.getPrice())
                        .active(p.getActive())
                        .build())
                .toList();

        Page<LowStockProductResponse> pageResult = new PageImpl<>(items, PageRequest.of(sanitizedPage, sanitizedSize), total);
        return PageResponse.fromPage(pageResult);
    }

    private Criteria buildDateRangeCriteria(String field, Instant from, Instant to) {
        Criteria criteria = new Criteria();
        appendDateRange(criteria, field, from, to);
        return criteria;
    }

    private void appendDateRange(Criteria criteria, String field, Instant from, Instant to) {
        if (from != null && to != null) {
            criteria.and(field).gte(from).lte(to);
        } else if (from != null) {
            criteria.and(field).gte(from);
        } else if (to != null) {
            criteria.and(field).lte(to);
        }
    }

    private static class AggregatedProduct {
        private final String productId;
        private final String productName;
        private long totalSold = 0L;
        private BigDecimal totalRevenue = BigDecimal.ZERO;

        public AggregatedProduct(String productId, String productName) {
            this.productId = productId;
            this.productName = productName;
        }

        public void addSales(long quantity, BigDecimal revenue) {
            this.totalSold += quantity;
            if (revenue != null) {
                this.totalRevenue = this.totalRevenue.add(revenue);
            }
        }

        public String getProductId() {
            return productId;
        }

        public String getProductName() {
            return productName;
        }

        public long getTotalSold() {
            return totalSold;
        }

        public BigDecimal getTotalRevenue() {
            return totalRevenue;
        }
    }
}

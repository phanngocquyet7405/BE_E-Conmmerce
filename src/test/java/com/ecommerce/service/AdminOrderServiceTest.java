package com.ecommerce.service;

import com.ecommerce.common.response.PageResponse;
import com.ecommerce.document.*;
import com.ecommerce.dto.request.AdminOrderCriteria;
import com.ecommerce.dto.response.AdminOrderDetailResponse;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.repository.*;
import com.ecommerce.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SePayTransactionRepository sePayTransactionRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private SePayService sePayService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = Order.builder()
                .id("ord-1")
                .orderCode("BETES12345678")
                .userId("user-1")
                .customerName("Nguyen Van B")
                .customerEmail("b@ecommerce.com")
                .customerPhone("0987654321")
                .status(OrderStatus.CONFIRMED)
                .paymentMethod(PaymentMethod.SEPAY)
                .paymentStatus(PaymentStatus.PAID)
                .totalAmount(new BigDecimal("200000"))
                .shippingFee(new BigDecimal("30000"))
                .finalAmount(new BigDecimal("230000"))
                .paidAmount(new BigDecimal("230000"))
                .createdAt(Instant.now())
                .items(List.of())
                .build();
    }

    @Test
    @DisplayName("Admin lấy danh sách đơn hàng có bộ lọc và phân trang chuẩn")
    void getOrdersForAdmin_WithFilters_BuildsExpectedQuery() {
        Instant from = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant to = Instant.now();

        AdminOrderCriteria criteria = AdminOrderCriteria.builder()
                .keyword("BETES12345678")
                .status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .paymentMethod(PaymentMethod.SEPAY)
                .from(from)
                .to(to)
                .page(0)
                .size(10)
                .sortBy("finalAmount")
                .sortDir("desc")
                .build();

        when(mongoTemplate.count(any(Query.class), eq(Order.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Order.class))).thenReturn(List.of(sampleOrder));

        PageResponse<OrderResponse> result = orderService.getOrdersForAdmin(criteria);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals("BETES12345678", result.getItems().get(0).getOrderCode());
        assertEquals(1L, result.getTotalElements());

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Order.class));
        Query captured = queryCaptor.getValue();
        assertNotNull(captured);
    }

    @Test
    @DisplayName("Admin lấy danh sách đơn hàng giới hạn trần phân trang [1, 50]")
    void getOrdersForAdmin_HardCapsPageSize() {
        AdminOrderCriteria criteria = AdminOrderCriteria.builder()
                .page(-5)
                .size(100) // Vượt trần 50
                .build();

        when(mongoTemplate.count(any(Query.class), eq(Order.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Order.class))).thenReturn(List.of());

        PageResponse<OrderResponse> result = orderService.getOrdersForAdmin(criteria);

        assertNotNull(result);
        assertEquals(0, result.getPageNo(), "Page âm phải được chuẩn hóa về 0");
        assertEquals(50, result.getPageSize(), "Size > 50 phải bị giới hạn về 50");
    }

    @Test
    @DisplayName("Admin lấy danh sách đơn hàng với sortBy không hợp lệ sẽ fallback về createdAt")
    void getOrdersForAdmin_InvalidSortField_FallsBackToCreatedAt() {
        AdminOrderCriteria criteria = AdminOrderCriteria.builder()
                .sortBy("maliciousField;dropTable")
                .build();

        when(mongoTemplate.count(any(Query.class), eq(Order.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Order.class))).thenReturn(List.of());

        PageResponse<OrderResponse> result = orderService.getOrdersForAdmin(criteria);
        assertNotNull(result);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(Order.class));
        assertTrue(captor.getValue().getSortObject().containsKey("createdAt"));
    }

    @Test
    @DisplayName("Admin xem chi tiết đơn hàng kèm danh sách giao dịch SePay liên quan")
    void getOrderDetailForAdmin_WhenExists_ReturnsOrderAndTransactions() {
        SePayTransaction tx1 = SePayTransaction.builder()
                .id("tx-1")
                .sepayId(1001L)
                .orderCode("BETES12345678")
                .amountIn(230000L)
                .transactionDate(LocalDateTime.now())
                .status(SePayTransactionStatus.PROCESSED)
                .build();

        when(orderRepository.findById("ord-1")).thenReturn(Optional.of(sampleOrder));
        when(sePayTransactionRepository.findByOrderCodeOrderByCreatedAtDesc("BETES12345678"))
                .thenReturn(List.of(tx1));

        AdminOrderDetailResponse detail = orderService.getOrderDetailForAdmin("ord-1");

        assertNotNull(detail);
        assertNotNull(detail.getOrder());
        assertEquals("BETES12345678", detail.getOrder().getOrderCode());
        assertEquals(1, detail.getTransactions().size());
        assertEquals(1001L, detail.getTransactions().get(0).getSepayId());
    }

    @Test
    @DisplayName("Admin xem chi tiết đơn hàng không tồn tại sẽ trả lỗi ORDER_NOT_FOUND (404)")
    void getOrderDetailForAdmin_WhenNotFound_ThrowsException() {
        when(orderRepository.findById("non-existent")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                orderService.getOrderDetailForAdmin("non-existent"));

        assertEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode());
    }
}

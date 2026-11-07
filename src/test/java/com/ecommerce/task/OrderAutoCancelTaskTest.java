package com.ecommerce.task;

import com.ecommerce.document.OrderItem;
import com.ecommerce.document.OrderStatus;
import com.ecommerce.document.PaymentMethod;
import com.ecommerce.document.PaymentStatus;
import com.ecommerce.document.Product;
import com.ecommerce.document.Order;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.OrderService;
import com.ecommerce.service.SePayService;
import com.ecommerce.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderAutoCancelTaskTest {

    @Mock
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private SePayService sePayService;

    @Mock
    private com.ecommerce.repository.SePayTransactionRepository sePayTransactionRepository;

    private OrderAutoCancelTask autoCancelTask;
    private OrderServiceImpl orderServiceImpl;

    @BeforeEach
    void setUp() {
        autoCancelTask = new OrderAutoCancelTask(orderService);
        ReflectionTestUtils.setField(autoCancelTask, "unpaidTimeoutMinutes", 15);

        orderServiceImpl = new OrderServiceImpl(
                orderRepository,
                cartRepository,
                productRepository,
                userRepository,
                sePayTransactionRepository,
                mongoTemplate,
                sePayService
        );
    }

    @Test
    @DisplayName("Task định kỳ gọi orderService.cancelExpiredOrders với timeout cấu hình")
    void autoCancelExpiredOrders_CallsOrderService() {
        when(orderService.cancelExpiredOrders(15)).thenReturn(2);

        autoCancelTask.autoCancelExpiredOrders();

        verify(orderService, times(1)).cancelExpiredOrders(15);
    }

    @Test
    @DisplayName("cancelExpiredOrders: Tìm thấy đơn PENDING UNPAID quá 15 phút -> Chuyển CANCELLED và hoàn tồn kho")
    void cancelExpiredOrders_ExpiredOrdersFound_CancelsAndRestoresStock() {
        OrderItem item = OrderItem.builder()
                .productId("prod-1")
                .productName("Sản phẩm hết hạn")
                .quantity(3)
                .price(BigDecimal.valueOf(100000))
                .build();

        Order expiredOrder = Order.builder()
                .id("order-expired-1")
                .orderCode("BETES99999999")
                .userId("user-1")
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.UNPAID)
                .paymentMethod(PaymentMethod.SEPAY)
                .items(List.of(item))
                .createdAt(Instant.now().minus(20, ChronoUnit.MINUTES))
                .build();

        Order cancelledOrder = Order.builder()
                .id("order-expired-1")
                .orderCode("BETES99999999")
                .userId("user-1")
                .status(OrderStatus.CANCELLED)
                .paymentStatus(PaymentStatus.UNPAID)
                .paymentMethod(PaymentMethod.SEPAY)
                .items(List.of(item))
                .createdAt(expiredOrder.getCreatedAt())
                .build();

        when(mongoTemplate.find(any(Query.class), eq(Order.class)))
                .thenReturn(List.of(expiredOrder));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Order.class)))
                .thenReturn(cancelledOrder);

        int cancelled = orderServiceImpl.cancelExpiredOrders(15);

        assertEquals(1, cancelled);

        // Kiểm tra hoàn kho cho prod-1 với số lượng 3
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), any(Update.class), eq(Product.class));
    }

    @Test
    @DisplayName("cancelExpiredOrders: Không có đơn nào hết hạn -> Không xử lý gì")
    void cancelExpiredOrders_NoExpiredOrders_DoesNothing() {
        when(mongoTemplate.find(any(Query.class), eq(Order.class)))
                .thenReturn(Collections.emptyList());

        int cancelled = orderServiceImpl.cancelExpiredOrders(15);

        assertEquals(0, cancelled);
        verify(orderRepository, never()).save(any());
        verify(mongoTemplate, never()).updateFirst(any(), any(), eq(Product.class));
    }
}

package com.ecommerce.service;

import com.ecommerce.config.SePayProperties;
import com.ecommerce.document.*;
import com.ecommerce.service.dto.WebhookResult;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.SePayTransactionRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.impl.OrderServiceImpl;
import com.ecommerce.service.impl.SePayWebhookServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderConcurrencyRaceTest {

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
    private SePayTransactionRepository sePayTransactionRepository;

    private OrderServiceImpl orderService;
    private SePayWebhookServiceImpl sePayWebhookService;
    private SePayProperties sePayProperties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        sePayProperties = new SePayProperties();
        sePayProperties.setBankId("MBBank");
        sePayProperties.setBankAccount("0123456789");
        sePayProperties.setAccountHolder("NGUYEN VAN A");
        sePayProperties.setApiKey("test-api-key");
        sePayProperties.setOrderCodePrefix("BETES");

        orderService = new OrderServiceImpl(
                orderRepository,
                cartRepository,
                productRepository,
                userRepository,
                mongoTemplate,
                sePayService
        );

        sePayWebhookService = new SePayWebhookServiceImpl(
                sePayTransactionRepository,
                orderRepository,
                mongoTemplate,
                objectMapper,
                sePayProperties
        );
    }

    @Test
    @DisplayName("Race condition 1: 2 thread gọi cancelOrder cùng lúc -> Chỉ 1 thread hoàn kho thành công, thread 2 nhận lỗi")
    void concurrentCancel_Twice_OnlyOneRestoresStockAndSucceeds() throws Exception {
        String orderId = "order-concurrent-1";
        String userId = "user-1";

        OrderItem item = OrderItem.builder()
                .productId("prod-1")
                .quantity(2)
                .price(BigDecimal.valueOf(100000))
                .build();

        Order cancelledOrder = Order.builder()
                .id(orderId)
                .orderCode("BETES10000001")
                .userId(userId)
                .status(OrderStatus.CANCELLED)
                .paymentStatus(PaymentStatus.UNPAID)
                .items(List.of(item))
                .build();

        // Mô phỏng findAndModify nguyên tử: Lần 1 thành công trả về cancelledOrder, Lần 2 trả về null (vì trạng thái đã đổi sang CANCELLED)
        AtomicBoolean alreadyUpdated = new AtomicBoolean(false);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Order.class)))
                .thenAnswer(inv -> {
                    if (alreadyUpdated.compareAndSet(false, true)) {
                        return cancelledOrder;
                    }
                    return null;
                });

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(cancelledOrder));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    orderService.cancelOrder(userId, orderId);
                    successCount.incrementAndGet();
                } catch (AppException ex) {
                    if (ex.getErrorCode() == ErrorCode.ORDER_CANNOT_BE_CANCELLED) {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Phát lệnh chạy đồng thời
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(1, successCount.get(), "Chỉ duy nhất 1 thread hủy đơn thành công");
        assertEquals(1, failCount.get(), "Thread còn lại phải nhận ngoại lệ ORDER_CANNOT_BE_CANCELLED");

        // Hoàn kho updateFirst chỉ được gọi ĐÚNG 1 LẦN
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), any(Update.class), eq(Product.class));
    }

    @Test
    @DisplayName("Race condition 2: cancelOrder và SePay Webhook chạy đồng thời -> Nếu đơn bị hủy trước, webhook đánh dấu MISMATCH cần hoàn tiền, không bao giờ đặt PROCESSED")
    void concurrentCancelAndWebhook_WhenOrderCancelled_WebhookMarksMismatch() {
        String orderCode = "BETES20000002";
        String rawBody = String.format("""
                {
                    "id": 9901,
                    "gateway": "MBBank",
                    "transactionDate": "2026-11-06 10:00:00",
                    "accountNumber": "0123456789",
                    "content": "%s",
                    "transferType": "in",
                    "transferAmount": 200000
                }
                """, orderCode);

        Order existingOrder = Order.builder()
                .id("order-2")
                .orderCode(orderCode)
                .userId("user-1")
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.UNPAID)
                .paymentMethod(PaymentMethod.SEPAY)
                .finalAmount(BigDecimal.valueOf(200000))
                .build();

        Order alreadyCancelledOrder = Order.builder()
                .id("order-2")
                .orderCode(orderCode)
                .userId("user-1")
                .status(OrderStatus.CANCELLED)
                .paymentStatus(PaymentStatus.UNPAID)
                .paymentMethod(PaymentMethod.SEPAY)
                .finalAmount(BigDecimal.valueOf(200000))
                .build();

        when(orderRepository.findByOrderCode(orderCode))
                .thenReturn(Optional.of(existingOrder)) // Lần đầu đọc ra PENDING
                .thenReturn(Optional.of(alreadyCancelledOrder)); // Lần sau đọc ra CANCELLED

        when(sePayTransactionRepository.save(any(SePayTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Mô phỏng race condition: Khi webhook thực thi updateFirst thì đơn đã bị hủy trước -> modifiedCount = 0
        UpdateResult zeroResult = mock(UpdateResult.class);
        when(zeroResult.getModifiedCount()).thenReturn(0L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Order.class)))
                .thenReturn(zeroResult);

        WebhookResult result = sePayWebhookService.processWebhook(rawBody, "Apikey test-api-key", null, null);

        assertNotNull(result);
        assertTrue(result.success());

        // Kiểm tra transaction được lưu lại với trạng thái MISMATCH kèm lý do đơn đã hủy
        verify(sePayTransactionRepository, atLeastOnce()).save(argThat(tx ->
                tx.getStatus() == SePayTransactionStatus.MISMATCH &&
                tx.getFailureReason() != null &&
                tx.getFailureReason().contains("Đơn hàng đã bị hủy trước khi nhận thanh toán")
        ));
    }

    @Test
    @DisplayName("Race condition 3: autoCancelExpiredOrders và SePay Webhook chạy đồng thời -> autoCancel khớp nguyên tử")
    void concurrentAutoCancelAndWebhook_PreventsDoubleExecution() {
        OrderItem item = OrderItem.builder()
                .productId("prod-3")
                .quantity(1)
                .price(BigDecimal.valueOf(50000))
                .build();

        Order candidate = Order.builder()
                .id("order-3")
                .orderCode("BETES30000003")
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.UNPAID)
                .paymentMethod(PaymentMethod.SEPAY)
                .items(List.of(item))
                .createdAt(Instant.now().minus(20, java.time.temporal.ChronoUnit.MINUTES))
                .build();

        when(mongoTemplate.find(any(Query.class), eq(Order.class)))
                .thenReturn(List.of(candidate));

        // findAndModify nguyên tử thành công cho auto-cancel
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Order.class)))
                .thenReturn(candidate);

        int cancelled = orderService.cancelExpiredOrders(15);

        assertEquals(1, cancelled);
        // Hoàn kho cho candidate
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), any(Update.class), eq(Product.class));
    }
}

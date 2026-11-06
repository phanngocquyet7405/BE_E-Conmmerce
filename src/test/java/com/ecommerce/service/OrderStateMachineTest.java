package com.ecommerce.service;

import com.ecommerce.document.*;
import com.ecommerce.dto.request.UpdateOrderStatusRequest;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderStateMachineTest {

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

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
                orderRepository,
                cartRepository,
                productRepository,
                userRepository,
                sePayTransactionRepository,
                mongoTemplate,
                sePayService
        );
    }

    private Order createSampleOrder(OrderStatus status, PaymentMethod method, PaymentStatus paymentStatus) {
        OrderItem item = OrderItem.builder()
                .productId("prod-1")
                .productName("Sản phẩm test")
                .quantity(2)
                .price(BigDecimal.valueOf(50000))
                .subTotal(BigDecimal.valueOf(100000))
                .build();

        return Order.builder()
                .id("order-1")
                .orderCode("BETES11112222")
                .userId("user-1")
                .status(status)
                .paymentMethod(method)
                .paymentStatus(paymentStatus)
                .items(List.of(item))
                .finalAmount(BigDecimal.valueOf(100000))
                .stockRestored(false)
                .build();
    }

    @Test
    @DisplayName("Luồng chuẩn: PENDING -> CONFIRMED -> SHIPPING -> DELIVERED thành công")
    void validLifecycle_Succeeds() {
        Order order = createSampleOrder(OrderStatus.PENDING, PaymentMethod.COD, PaymentStatus.UNPAID);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // 1. PENDING -> CONFIRMED
        UpdateOrderStatusRequest req1 = new UpdateOrderStatusRequest();
        req1.setStatus(OrderStatus.CONFIRMED);
        OrderResponse res1 = orderService.updateOrderStatus("order-1", req1);
        assertEquals(OrderStatus.CONFIRMED, res1.getStatus());

        // 2. CONFIRMED -> SHIPPING
        UpdateOrderStatusRequest req2 = new UpdateOrderStatusRequest();
        req2.setStatus(OrderStatus.SHIPPING);
        OrderResponse res2 = orderService.updateOrderStatus("order-1", req2);
        assertEquals(OrderStatus.SHIPPING, res2.getStatus());

        // 3. SHIPPING -> DELIVERED (COD tự động chuyển sang PAID)
        UpdateOrderStatusRequest req3 = new UpdateOrderStatusRequest();
        req3.setStatus(OrderStatus.DELIVERED);
        OrderResponse res3 = orderService.updateOrderStatus("order-1", req3);
        assertEquals(OrderStatus.DELIVERED, res3.getStatus());
        assertEquals(PaymentStatus.PAID, res3.getPaymentStatus());
    }

    @Test
    @DisplayName("D4: Chặn chuyển sang SHIPPING cho đơn SEPAY khi paymentStatus != PAID")
    void d4_BlockShipping_WhenSepayUnpaid_ThrowsException() {
        Order order = createSampleOrder(OrderStatus.CONFIRMED, PaymentMethod.SEPAY, PaymentStatus.UNPAID);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
        req.setStatus(OrderStatus.SHIPPING);

        AppException ex = assertThrows(AppException.class, () -> orderService.updateOrderStatus("order-1", req));
        assertEquals(ErrorCode.PAYMENT_REQUIRED_BEFORE_SHIPPING, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("chưa thanh toán"));

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("D4: Cho phép chuyển sang SHIPPING cho đơn SEPAY khi paymentStatus == PAID")
    void d4_AllowShipping_WhenSepayPaid_Succeeds() {
        Order order = createSampleOrder(OrderStatus.CONFIRMED, PaymentMethod.SEPAY, PaymentStatus.PAID);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
        req.setStatus(OrderStatus.SHIPPING);

        OrderResponse res = orderService.updateOrderStatus("order-1", req);
        assertEquals(OrderStatus.SHIPPING, res.getStatus());
    }

    @Test
    @DisplayName("Chuyển trạng thái lùi hoặc sai luồng -> Ném INVALID_ORDER_STATUS_TRANSITION")
    void invalidTransition_ThrowsException() {
        // Đơn đã giao (DELIVERED) cố quay lại SHIPPING
        Order deliveredOrder = createSampleOrder(OrderStatus.DELIVERED, PaymentMethod.COD, PaymentStatus.PAID);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(deliveredOrder));

        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
        req.setStatus(OrderStatus.SHIPPING);

        AppException ex = assertThrows(AppException.class, () -> orderService.updateOrderStatus("order-1", req));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());

        // Đơn đang giao (SHIPPING) cố quay lại CONFIRMED
        Order shippingOrder = createSampleOrder(OrderStatus.SHIPPING, PaymentMethod.COD, PaymentStatus.UNPAID);
        when(orderRepository.findById("order-2")).thenReturn(Optional.of(shippingOrder));

        UpdateOrderStatusRequest req2 = new UpdateOrderStatusRequest();
        req2.setStatus(OrderStatus.CONFIRMED);

        AppException ex2 = assertThrows(AppException.class, () -> orderService.updateOrderStatus("order-2", req2));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex2.getErrorCode());
    }

    @Test
    @DisplayName("D7: Hủy đơn đã PAID -> Chuyển sang AWAITING_REFUND, hoàn kho; confirmRefund chuyển sang CANCELLED và REFUNDED")
    void d7_CancelPaidOrder_TransitionsAwaitingRefund_ThenConfirmRefund() {
        Order paidOrder = createSampleOrder(OrderStatus.CONFIRMED, PaymentMethod.SEPAY, PaymentStatus.PAID);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(paidOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // 1. Admin yêu cầu hủy đơn đã PAID
        UpdateOrderStatusRequest cancelReq = new UpdateOrderStatusRequest();
        cancelReq.setStatus(OrderStatus.CANCELLED);

        OrderResponse cancelRes = orderService.updateOrderStatus("order-1", cancelReq);
        assertEquals(OrderStatus.AWAITING_REFUND, cancelRes.getStatus());
        assertTrue(paidOrder.getStockRestored());

        // Xác nhận tồn kho đã được hoàn 1 lần
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), any(Update.class), eq(Product.class));

        // 2. Admin bấm xác nhận đã hoàn tiền thủ công (confirmRefund)
        OrderResponse refundRes = orderService.confirmRefund("order-1");
        assertEquals(OrderStatus.CANCELLED, refundRes.getStatus());
        assertEquals(PaymentStatus.REFUNDED, refundRes.getPaymentStatus());

        // Xác nhận tồn kho KHÔNG bị hoàn lại lần 2 (idempotent)
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), any(Update.class), eq(Product.class));
    }
}

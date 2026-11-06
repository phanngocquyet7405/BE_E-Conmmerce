package com.ecommerce.service;

import com.ecommerce.document.Order;
import com.ecommerce.document.OrderStatus;
import com.ecommerce.document.PaymentMethod;
import com.ecommerce.document.PaymentStatus;
import com.ecommerce.dto.response.PaymentQrResponse;
import com.ecommerce.dto.response.PaymentStatusResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SePayService sePayService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = Order.builder()
                .id("order-123")
                .orderCode("BETES88888888")
                .userId("user-1")
                .totalAmount(BigDecimal.valueOf(100000))
                .shippingFee(BigDecimal.valueOf(30000))
                .finalAmount(BigDecimal.valueOf(130000))
                .paymentMethod(PaymentMethod.SEPAY)
                .paymentStatus(PaymentStatus.UNPAID)
                .status(OrderStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("1. Lấy thông tin VietQR thành công khi đơn hàng tồn tại")
    void getPaymentQrInfo_Success() {
        when(orderRepository.findById("order-123")).thenReturn(Optional.of(sampleOrder));
        when(sePayService.getBankAccount()).thenReturn("0862483762");
        when(sePayService.getBankId()).thenReturn("MBBank");
        when(sePayService.getAccountHolder()).thenReturn("NGUYEN VAN A");
        when(sePayService.buildQrUrl(130000L, "BETES88888888"))
                .thenReturn("https://qr.sepay.vn/img?acc=MBBank-0862483762&template=compact&amount=130000&des=BETES88888888");

        PaymentQrResponse response = paymentService.getPaymentQrInfo("order-123");

        assertNotNull(response);
        assertEquals("order-123", response.getOrderId());
        assertEquals("BETES88888888", response.getOrderCode());
        assertEquals(BigDecimal.valueOf(130000), response.getAmount());
        assertEquals("0862483762", response.getAccountNumber());
        assertEquals("MBBank", response.getBankName());
        assertEquals("NGUYEN VAN A", response.getAccountHolder());
        assertEquals(PaymentMethod.SEPAY, response.getPaymentMethod());
        assertEquals(PaymentStatus.UNPAID, response.getPaymentStatus());
        assertNotNull(response.getQrImageUrl());
        assertTrue(response.getPaymentInstruction().contains("BETES88888888"));
    }

    @Test
    @DisplayName("2. Lấy thông tin VietQR ném ORDER_NOT_FOUND khi không tìm thấy đơn")
    void getPaymentQrInfo_OrderNotFound_ThrowsAppException() {
        when(orderRepository.findById("order-999")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> paymentService.getPaymentQrInfo("order-999"));
        assertEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("3. Kiểm tra trạng thái thanh toán đơn hàng UNPAID")
    void checkPaymentStatus_UnpaidOrder_Success() {
        when(orderRepository.findById("order-123")).thenReturn(Optional.of(sampleOrder));

        PaymentStatusResponse response = paymentService.checkPaymentStatus("order-123");

        assertNotNull(response);
        assertEquals("order-123", response.getOrderId());
        assertEquals("BETES88888888", response.getOrderCode());
        assertEquals(PaymentStatus.UNPAID, response.getPaymentStatus());
        assertEquals(OrderStatus.PENDING, response.getOrderStatus());
        assertNull(response.getPaidAt());
        assertNull(response.getSepayTransactionId());
    }

    @Test
    @DisplayName("4. Kiểm tra trạng thái thanh toán đơn hàng đã PAID thành công")
    void checkPaymentStatus_PaidOrder_Success() {
        Instant paidTime = Instant.now();
        sampleOrder.setPaymentStatus(PaymentStatus.PAID);
        sampleOrder.setStatus(OrderStatus.CONFIRMED);
        sampleOrder.setPaidAt(paidTime);
        sampleOrder.setSepayTransactionId("sepay-99999");

        when(orderRepository.findById("order-123")).thenReturn(Optional.of(sampleOrder));

        PaymentStatusResponse response = paymentService.checkPaymentStatus("order-123");

        assertNotNull(response);
        assertEquals(PaymentStatus.PAID, response.getPaymentStatus());
        assertEquals(OrderStatus.CONFIRMED, response.getOrderStatus());
        assertEquals(paidTime, response.getPaidAt());
        assertEquals("sepay-99999", response.getSepayTransactionId());
    }

    @Test
    @DisplayName("5. Kiểm tra trạng thái thanh toán ném ORDER_NOT_FOUND khi không tìm thấy đơn")
    void checkPaymentStatus_OrderNotFound_ThrowsAppException() {
        when(orderRepository.findById("order-999")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> paymentService.checkPaymentStatus("order-999"));
        assertEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode());
    }
}

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
    @DisplayName("1. Lấy thông tin VietQR thành công khi đúng chủ đơn hàng")
    void getPaymentQrInfo_Success_WhenOwner() {
        when(orderRepository.findById("order-123")).thenReturn(Optional.of(sampleOrder));
        when(sePayService.getBankAccount()).thenReturn("0862483762");
        when(sePayService.getBankId()).thenReturn("MBBank");
        when(sePayService.getAccountHolder()).thenReturn("NGUYEN VAN A");
        when(sePayService.buildQrUrl(130000L, "BETES88888888"))
                .thenReturn("https://qr.sepay.vn/img?acc=MBBank-0862483762&template=compact&amount=130000&des=BETES88888888");

        PaymentQrResponse response = paymentService.getPaymentQrInfo("user-1", false, "order-123");

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
    }

    @Test
    @DisplayName("2. Lấy thông tin VietQR thành công khi người xem là Admin (kể cả không phải chủ đơn)")
    void getPaymentQrInfo_Success_WhenAdmin() {
        when(orderRepository.findById("order-123")).thenReturn(Optional.of(sampleOrder));
        when(sePayService.getBankAccount()).thenReturn("0862483762");
        when(sePayService.getBankId()).thenReturn("MBBank");
        when(sePayService.getAccountHolder()).thenReturn("NGUYEN VAN A");
        when(sePayService.buildQrUrl(130000L, "BETES88888888"))
                .thenReturn("https://qr.sepay.vn/img?acc=MBBank-0862483762&template=compact&amount=130000&des=BETES88888888");

        PaymentQrResponse response = paymentService.getPaymentQrInfo("admin-user", true, "order-123");

        assertNotNull(response);
        assertEquals("order-123", response.getOrderId());
    }

    @Test
    @DisplayName("3. IDOR: Người dùng khác cố lấy VietQR của đơn hàng -> Ném ORDER_NOT_FOUND")
    void getPaymentQrInfo_IDOR_ThrowsOrderNotFound() {
        when(orderRepository.findById("order-123")).thenReturn(Optional.of(sampleOrder));

        AppException ex = assertThrows(AppException.class, () ->
                paymentService.getPaymentQrInfo("attacker-user", false, "order-123"));

        assertEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("4. Lấy thông tin VietQR ném ORDER_NOT_FOUND khi không tìm thấy đơn")
    void getPaymentQrInfo_OrderNotFound_ThrowsAppException() {
        when(orderRepository.findById("order-999")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                paymentService.getPaymentQrInfo("user-1", false, "order-999"));

        assertEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("5. Kiểm tra trạng thái thanh toán thành công khi đúng chủ đơn")
    void checkPaymentStatus_Success_WhenOwner() {
        when(orderRepository.findById("order-123")).thenReturn(Optional.of(sampleOrder));

        PaymentStatusResponse response = paymentService.checkPaymentStatus("user-1", false, "order-123");

        assertNotNull(response);
        assertEquals("order-123", response.getOrderId());
        assertEquals("BETES88888888", response.getOrderCode());
        assertEquals(PaymentStatus.UNPAID, response.getPaymentStatus());
    }

    @Test
    @DisplayName("6. Kiểm tra trạng thái thanh toán thành công khi là Admin")
    void checkPaymentStatus_Success_WhenAdmin() {
        when(orderRepository.findById("order-123")).thenReturn(Optional.of(sampleOrder));

        PaymentStatusResponse response = paymentService.checkPaymentStatus("admin-user", true, "order-123");

        assertNotNull(response);
        assertEquals("order-123", response.getOrderId());
    }

    @Test
    @DisplayName("7. IDOR: Người dùng khác cố kiểm tra trạng thái thanh toán -> Ném ORDER_NOT_FOUND")
    void checkPaymentStatus_IDOR_ThrowsOrderNotFound() {
        when(orderRepository.findById("order-123")).thenReturn(Optional.of(sampleOrder));

        AppException ex = assertThrows(AppException.class, () ->
                paymentService.checkPaymentStatus("attacker-user", false, "order-123"));

        assertEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("8. Kiểm tra trạng thái thanh toán ném ORDER_NOT_FOUND khi không tìm thấy đơn")
    void checkPaymentStatus_OrderNotFound_ThrowsAppException() {
        when(orderRepository.findById("order-999")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                paymentService.checkPaymentStatus("user-1", false, "order-999"));

        assertEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode());
    }
}

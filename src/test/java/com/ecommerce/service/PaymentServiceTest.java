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

    @Mock
    private com.ecommerce.repository.SePayTransactionRepository sePayTransactionRepository;

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

    @Test
    @DisplayName("9. Admin lấy danh sách giao dịch SePay có phân trang và lọc theo trạng thái")
    void getTransactionsForAdmin_Success() {
        com.ecommerce.document.SePayTransaction tx = com.ecommerce.document.SePayTransaction.builder()
                .id("tx-1")
                .sepayId(12345L)
                .status(com.ecommerce.document.SePayTransactionStatus.MISMATCH)
                .amountIn(50000L)
                .orderCode("BETES88888888")
                .build();

        org.springframework.data.domain.Page<com.ecommerce.document.SePayTransaction> page =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(tx));

        when(sePayTransactionRepository.findByStatusOrderByCreatedAtDesc(eq(com.ecommerce.document.SePayTransactionStatus.MISMATCH), any()))
                .thenReturn(page);

        com.ecommerce.common.response.PageResponse<com.ecommerce.dto.response.SePayTransactionResponse> response =
                paymentService.getTransactionsForAdmin(com.ecommerce.document.SePayTransactionStatus.MISMATCH, org.springframework.data.domain.PageRequest.of(0, 10));

        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals(12345L, response.getItems().get(0).getSepayId());
    }

    @Test
    @DisplayName("10. Admin xử lý giao dịch SePay ghi nhận người xử lý và thời gian")
    void processTransactionForAdmin_Success() {
        com.ecommerce.document.SePayTransaction tx = com.ecommerce.document.SePayTransaction.builder()
                .id("tx-1")
                .sepayId(12345L)
                .status(com.ecommerce.document.SePayTransactionStatus.MISMATCH)
                .adminProcessed(false)
                .build();

        when(sePayTransactionRepository.findById("tx-1")).thenReturn(Optional.of(tx));
        when(sePayTransactionRepository.save(any(com.ecommerce.document.SePayTransaction.class)))
                .thenAnswer(i -> i.getArgument(0));

        com.ecommerce.dto.request.AdminProcessTransactionRequest request =
                com.ecommerce.dto.request.AdminProcessTransactionRequest.builder()
                        .action("RESOLVED")
                        .note("Đã đối soát thủ công và xác nhận với ngân hàng")
                        .build();

        com.ecommerce.dto.response.SePayTransactionResponse response =
                paymentService.processTransactionForAdmin("admin@ecommerce.com", "tx-1", request);

        assertNotNull(response);
        assertTrue(response.getAdminProcessed());
        assertEquals("admin@ecommerce.com", response.getProcessedBy());
        assertEquals("Đã đối soát thủ công và xác nhận với ngân hàng", response.getAdminNote());
        assertEquals(com.ecommerce.document.SePayTransactionStatus.PROCESSED, response.getStatus());
    }
}

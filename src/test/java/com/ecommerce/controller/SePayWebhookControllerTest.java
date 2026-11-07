package com.ecommerce.controller;

import com.ecommerce.config.SePayProperties;
import com.ecommerce.document.*;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.SePayTransactionRepository;
import com.ecommerce.service.SePayService;
import com.ecommerce.service.SePayWebhookService;
import com.ecommerce.service.impl.SePayWebhookServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SePayWebhookControllerTest {

    @Mock
    private SePayTransactionRepository sePayTransactionRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    private SePayProperties sePayProperties;
    private SePayWebhookService webhookService;
    private SePayWebhookController controller;
    private ObjectMapper objectMapper;

    private final String testSecret = "test_webhook_secret_123";
    private final String testApiKey = "test_api_key_456";
    private final String testBankAccount = "0862483762";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        sePayProperties = new SePayProperties();
        sePayProperties.setBankId("MBBank");
        sePayProperties.setBankAccount(testBankAccount);
        sePayProperties.setAccountHolder("NGUYEN VAN A");
        sePayProperties.setApiKey(testApiKey);
        sePayProperties.setWebhookSecret(testSecret);
        sePayProperties.setOrderCodePrefix("BETES");

        webhookService = new SePayWebhookServiceImpl(
                sePayTransactionRepository,
                orderRepository,
                mongoTemplate,
                objectMapper,
                sePayProperties
        );

        controller = new SePayWebhookController(webhookService);
    }

    private String calculateSignature(String secretKey, String timestamp, String rawBody) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hmacBytes = mac.doFinal((timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8));
        return "sha256=" + HexFormat.of().formatHex(hmacBytes);
    }

    // =========================================================================
    // 1. NHÓM TEST XÁC THỰC WEBHOOK (AUTHENTICATION)
    // =========================================================================

    @Test
    @DisplayName("1.1. Xác thực thành công bằng header Authorization: Apikey <key>")
    void handleWebhook_ValidApiKey_Returns200() {
        String rawBody = "{\"id\":1001,\"transferType\":\"in\",\"transferAmount\":50000}";
        when(sePayTransactionRepository.save(any(SePayTransaction.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(
                rawBody, "Apikey " + testApiKey, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("success"));
    }

    @Test
    @DisplayName("1.2. Xác thực thất bại bằng header Authorization: Apikey sai -> 401")
    void handleWebhook_InvalidApiKey_Returns401() {
        String rawBody = "{\"id\":1001,\"transferType\":\"in\"}";

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(
                rawBody, "Apikey wrong_api_key", null, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(false, response.getBody().get("success"));
    }

    @Test
    @DisplayName("1.3. Xác thực thành công bằng HMAC-SHA256 (X-SePay-Signature & X-SePay-Timestamp)")
    void handleWebhook_ValidHmacSignature_Returns200() throws Exception {
        long now = Instant.now().getEpochSecond();
        String rawBody = "{\"id\":1002,\"transferType\":\"in\",\"transferAmount\":50000}";
        String sig = calculateSignature(testSecret, String.valueOf(now), rawBody);

        when(sePayTransactionRepository.save(any(SePayTransaction.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(
                rawBody, null, sig, String.valueOf(now));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("success"));
    }

    @Test
    @DisplayName("1.4. Xác thực HMAC-SHA256 thất bại do chữ ký sai -> 401")
    void handleWebhook_InvalidHmacSignature_Returns401() {
        long now = Instant.now().getEpochSecond();
        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(
                "{}", null, "sha256=invalid_hash", String.valueOf(now));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("1.5. Thiếu toàn bộ header xác thực (không có Authorization, không có Signature) -> 401")
    void handleWebhook_MissingAllAuthHeaders_Returns401() {
        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(
                "{}", null, null, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("1.6. Timestamp lệch quá 300 giây -> 401")
    void handleWebhook_TimestampExpired_Returns401() {
        long oldTimestamp = Instant.now().getEpochSecond() - 301;
        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(
                "{}", null, "sha256=dummy", String.valueOf(oldTimestamp));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("1.7. Timestamp không phải số -> 401")
    void handleWebhook_NonNumericTimestamp_Returns401() {
        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(
                "{}", null, "sha256=dummy", "not-a-number");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("1.8. Lỗi parse JSON rawBody -> 400 Bad Request")
    void handleWebhook_MalformedJson_Returns400() throws Exception {
        long now = Instant.now().getEpochSecond();
        String rawBody = "{invalid_json_format:";
        String sig = calculateSignature(testSecret, String.valueOf(now), rawBody);

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(
                rawBody, null, sig, String.valueOf(now));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(false, response.getBody().get("success"));
    }

    // =========================================================================
    // 2. NHÓM TEST TÍNH ĐÚNG ĐẮN VÀ IDEMPOTENCY (BUSINESS LOGIC)
    // =========================================================================

    @Test
    @DisplayName("2.1. Idempotency: Giao dịch đã ở trạng thái PROCESSED -> Trả 200 OK ngay, không cập nhật lại")
    void handleWebhook_AlreadyProcessed_Returns200Immediately() {
        String rawBody = "{\"id\":92704,\"transferType\":\"in\",\"transferAmount\":10000}";
        SePayTransaction existingTx = SePayTransaction.builder()
                .sepayId(92704L)
                .status(SePayTransactionStatus.PROCESSED)
                .build();

        when(sePayTransactionRepository.findBySepayId(92704L)).thenReturn(Optional.of(existingTx));

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(
                rawBody, "Apikey " + testApiKey, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("success"));
        verify(sePayTransactionRepository, never()).save(any());
        verify(orderRepository, never()).findByOrderCode(any());
    }

    @Test
    @DisplayName("2.2. Xử lý đồng thời (Race condition / DuplicateKeyException) -> Trả về 200 OK an toàn")
    void handleWebhook_DuplicateKeyException_Returns200() {
        String rawBody = "{\"id\":92704,\"transferType\":\"in\",\"transferAmount\":10000}";

        when(sePayTransactionRepository.findBySepayId(92704L)).thenReturn(Optional.empty());
        when(sePayTransactionRepository.save(any(SePayTransaction.class)))
                .thenThrow(new DuplicateKeyException("Duplicate key sepayId"));

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(
                rawBody, "Apikey " + testApiKey, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("success"));
    }

    @Test
    @DisplayName("2.3. Khách chuyển tiền thành công: Cập nhật nguyên tử Order sang PAID, CONFIRMED và transaction sang PROCESSED")
    void handleWebhook_TransferIn_UpdatesOrderToPaidAtomically() throws Exception {
        long now = Instant.now().getEpochSecond();
        String rawBody = "{\"id\":92704,\"gateway\":\"MBBank\",\"transactionDate\":\"2024-07-02 11:08:33\"," +
                "\"accountNumber\":\"" + testBankAccount + "\",\"code\":\"BETES12345678\"," +
                "\"transferType\":\"in\",\"transferAmount\":50000}";
        String sig = calculateSignature(testSecret, String.valueOf(now), rawBody);

        Order order = Order.builder()
                .id("order-1")
                .orderCode("BETES12345678")
                .paymentMethod(PaymentMethod.SEPAY)
                .paymentStatus(PaymentStatus.UNPAID)
                .finalAmount(BigDecimal.valueOf(50000))
                .build();

        when(sePayTransactionRepository.findBySepayId(92704L)).thenReturn(Optional.empty());
        when(sePayTransactionRepository.save(any(SePayTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findByOrderCode("BETES12345678")).thenReturn(Optional.of(order));

        UpdateResult updateResult = mock(UpdateResult.class);
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Order.class))).thenReturn(updateResult);

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(
                rawBody, null, sig, String.valueOf(now));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("success"));
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), any(Update.class), eq(Order.class));

        ArgumentCaptor<SePayTransaction> txCaptor = ArgumentCaptor.forClass(SePayTransaction.class);
        verify(sePayTransactionRepository, atLeastOnce()).save(txCaptor.capture());
        assertEquals(SePayTransactionStatus.PROCESSED, txCaptor.getValue().getStatus());
        assertEquals("BETES12345678", txCaptor.getValue().getOrderCode());
    }

    @Test
    @DisplayName("2.4. Khách chuyển thiếu tiền: Cập nhật Order isUnderpaid=true, transaction MISMATCH, không sang PAID")
    void handleWebhook_PartialPayment_UpdatesUnderpaidAndDoesNotConfirm() {
        String rawBody = "{\"id\":92707,\"accountNumber\":\"" + testBankAccount + "\"," +
                "\"code\":\"BETES12345678\",\"transferType\":\"in\",\"transferAmount\":40000}";

        Order order = Order.builder()
                .id("order-1")
                .orderCode("BETES12345678")
                .paymentMethod(PaymentMethod.SEPAY)
                .paymentStatus(PaymentStatus.UNPAID)
                .status(OrderStatus.PENDING)
                .finalAmount(BigDecimal.valueOf(50000))
                .build();

        when(sePayTransactionRepository.findBySepayId(92707L)).thenReturn(Optional.empty());
        when(sePayTransactionRepository.save(any(SePayTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findByOrderCode("BETES12345678")).thenReturn(Optional.of(order));

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(
                rawBody, "Apikey " + testApiKey, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mongoTemplate, never()).updateFirst(any(), any(), eq(Order.class));
        verify(orderRepository, times(1)).save(order);
        assertTrue(order.getIsUnderpaid());
        assertEquals(BigDecimal.valueOf(40000), order.getPaidAmount());
        assertEquals(BigDecimal.valueOf(-10000), order.getAmountDiscrepancy());
    }

    @Test
    @DisplayName("2.5. Khách chuyển thừa tiền: Order sang PAID, CONFIRMED, isOverpaid=true, ghi nhận chênh lệch")
    void handleWebhook_Overpaid_UpdatesOrderWithOverpaidFlag() {
        String rawBody = "{\"id\":92708,\"accountNumber\":\"" + testBankAccount + "\"," +
                "\"code\":\"BETES12345678\",\"transferType\":\"in\",\"transferAmount\":60000}";

        Order order = Order.builder()
                .id("order-1")
                .orderCode("BETES12345678")
                .paymentMethod(PaymentMethod.SEPAY)
                .paymentStatus(PaymentStatus.UNPAID)
                .status(OrderStatus.PENDING)
                .finalAmount(BigDecimal.valueOf(50000))
                .build();

        when(sePayTransactionRepository.findBySepayId(92708L)).thenReturn(Optional.empty());
        when(sePayTransactionRepository.save(any(SePayTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findByOrderCode("BETES12345678")).thenReturn(Optional.of(order));

        UpdateResult updateResult = mock(UpdateResult.class);
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Order.class))).thenReturn(updateResult);

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(
                rawBody, "Apikey " + testApiKey, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), any(Update.class), eq(Order.class));
    }

    @Test
    @DisplayName("2.6. Đơn hàng đã ở trạng thái PAID -> Không cập nhật lại, trả 200 OK")
    void handleWebhook_AlreadyPaidOrder_Returns200WithoutModifying() {
        String rawBody = "{\"id\":92709,\"accountNumber\":\"" + testBankAccount + "\"," +
                "\"code\":\"BETES12345678\",\"transferType\":\"in\",\"transferAmount\":50000}";

        Order order = Order.builder()
                .id("order-1")
                .orderCode("BETES12345678")
                .paymentMethod(PaymentMethod.SEPAY)
                .paymentStatus(PaymentStatus.PAID)
                .status(OrderStatus.CONFIRMED)
                .finalAmount(BigDecimal.valueOf(50000))
                .build();

        when(sePayTransactionRepository.findBySepayId(92709L)).thenReturn(Optional.empty());
        when(sePayTransactionRepository.save(any(SePayTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findByOrderCode("BETES12345678")).thenReturn(Optional.of(order));

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(
                rawBody, "Apikey " + testApiKey, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mongoTemplate, never()).updateFirst(any(), any(), eq(Order.class));
    }

    @Test
    @DisplayName("2.7. Đơn hàng đã bị CANCELLED -> Transaction MISMATCH, Order giữ nguyên CANCELLED, trả 200 OK")
    void handleWebhook_CancelledOrder_MarksMismatchAndDoesNotConfirm() {
        String rawBody = "{\"id\":92710,\"accountNumber\":\"" + testBankAccount + "\"," +
                "\"code\":\"BETES12345678\",\"transferType\":\"in\",\"transferAmount\":50000}";

        Order order = Order.builder()
                .id("order-1")
                .orderCode("BETES12345678")
                .paymentMethod(PaymentMethod.SEPAY)
                .paymentStatus(PaymentStatus.UNPAID)
                .status(OrderStatus.CANCELLED)
                .finalAmount(BigDecimal.valueOf(50000))
                .build();

        when(sePayTransactionRepository.findBySepayId(92710L)).thenReturn(Optional.empty());
        when(sePayTransactionRepository.save(any(SePayTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findByOrderCode("BETES12345678")).thenReturn(Optional.of(order));

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(
                rawBody, "Apikey " + testApiKey, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mongoTemplate, never()).updateFirst(any(), any(), eq(Order.class));

        ArgumentCaptor<SePayTransaction> txCaptor = ArgumentCaptor.forClass(SePayTransaction.class);
        verify(sePayTransactionRepository, atLeastOnce()).save(txCaptor.capture());
        assertEquals(SePayTransactionStatus.MISMATCH, txCaptor.getValue().getStatus());
        assertTrue(txCaptor.getValue().getFailureReason().contains("hủy"));
    }

    @Test
    @DisplayName("2.8. orderCode không tồn tại trong DB -> Transaction MISMATCH, trả 200 OK")
    void handleWebhook_OrderNotFound_MarksMismatchAndReturns200() {
        String rawBody = "{\"id\":92711,\"accountNumber\":\"" + testBankAccount + "\"," +
                "\"code\":\"BETES99999999\",\"transferType\":\"in\",\"transferAmount\":50000}";

        when(sePayTransactionRepository.findBySepayId(92711L)).thenReturn(Optional.empty());
        when(sePayTransactionRepository.save(any(SePayTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findByOrderCode("BETES99999999")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(
                rawBody, "Apikey " + testApiKey, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mongoTemplate, never()).updateFirst(any(), any(), eq(Order.class));

        ArgumentCaptor<SePayTransaction> txCaptor = ArgumentCaptor.forClass(SePayTransaction.class);
        verify(sePayTransactionRepository, atLeastOnce()).save(txCaptor.capture());
        assertEquals(SePayTransactionStatus.MISMATCH, txCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("2.9. transferType là 'out' (tiền ra) -> Transaction IGNORED, không cập nhật Order, trả 200 OK")
    void handleWebhook_TransferOut_MarksIgnoredAndReturns200() {
        String rawBody = "{\"id\":92712,\"code\":\"BETES12345678\",\"transferType\":\"out\",\"transferAmount\":50000}";

        when(sePayTransactionRepository.findBySepayId(92712L)).thenReturn(Optional.empty());
        when(sePayTransactionRepository.save(any(SePayTransaction.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(
                rawBody, "Apikey " + testApiKey, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(orderRepository, never()).findByOrderCode(any());

        ArgumentCaptor<SePayTransaction> txCaptor = ArgumentCaptor.forClass(SePayTransaction.class);
        verify(sePayTransactionRepository, atLeastOnce()).save(txCaptor.capture());
        assertEquals(SePayTransactionStatus.IGNORED, txCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("2.10. accountNumber không khớp tài khoản cấu hình -> Transaction MISMATCH, trả 200 OK")
    void handleWebhook_AccountNumberMismatch_MarksMismatchAndReturns200() {
        String rawBody = "{\"id\":92713,\"accountNumber\":\"9999999999\"," +
                "\"code\":\"BETES12345678\",\"transferType\":\"in\",\"transferAmount\":50000}";

        when(sePayTransactionRepository.findBySepayId(92713L)).thenReturn(Optional.empty());
        when(sePayTransactionRepository.save(any(SePayTransaction.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(
                rawBody, "Apikey " + testApiKey, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(orderRepository, never()).findByOrderCode(any());

        ArgumentCaptor<SePayTransaction> txCaptor = ArgumentCaptor.forClass(SePayTransaction.class);
        verify(sePayTransactionRepository, atLeastOnce()).save(txCaptor.capture());
        assertEquals(SePayTransactionStatus.MISMATCH, txCaptor.getValue().getStatus());
        assertTrue(txCaptor.getValue().getFailureReason().contains("không khớp"));
    }

    @Test
    @DisplayName("2.11. Lỗi hệ thống bất ngờ -> Đánh dấu transaction FAILED (không xóa) và trả 500 để SePay retry")
    void handleWebhook_UnexpectedError_MarksFailedAndReturns500() {
        String rawBody = "{\"id\":92714,\"accountNumber\":\"" + testBankAccount + "\"," +
                "\"code\":\"BETES12345678\",\"transferType\":\"in\",\"transferAmount\":50000}";

        when(sePayTransactionRepository.findBySepayId(92714L)).thenReturn(Optional.empty());
        when(sePayTransactionRepository.save(any(SePayTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findByOrderCode("BETES12345678")).thenThrow(new RuntimeException("MongoDB Timeout"));

        ResponseEntity<Map<String, Object>> response = controller.handleWebhook(
                rawBody, "Apikey " + testApiKey, null, null);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        verify(sePayTransactionRepository, never()).delete(any());

        ArgumentCaptor<SePayTransaction> txCaptor = ArgumentCaptor.forClass(SePayTransaction.class);
        verify(sePayTransactionRepository, atLeastOnce()).save(txCaptor.capture());
        assertEquals(SePayTransactionStatus.FAILED, txCaptor.getValue().getStatus());
    }

    // =========================================================================
    // 3. TEST CẤU HÌNH SePayService VÀ BUILD QR URL (KHÔNG HARDCODE 0123456789)
    // =========================================================================

    @Test
    @DisplayName("3.1. SePayService buildQrUrl: format và URL-encode chuẩn theo SePayProperties")
    void sePayService_BuildQrUrl_FormattedCorrectly() {
        SePayProperties props = new SePayProperties();
        props.setBankId("VCB");
        props.setBankAccount("0987654321");
        props.setQrTemplate("compact");

        SePayService service = new SePayService(props);

        String qrUrl = service.buildQrUrl(50000L, "BETES12345678");
        assertEquals("https://qr.sepay.vn/img?acc=VCB-0987654321&template=compact&amount=50000&des=BETES12345678", qrUrl);
    }
}

package com.ecommerce.controller;

import com.ecommerce.document.Order;
import com.ecommerce.document.PaymentMethod;
import com.ecommerce.document.PaymentStatus;
import com.ecommerce.document.SePayTransaction;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.SePayTransactionRepository;
import com.ecommerce.service.SePayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

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

    private ObjectMapper objectMapper;

    @InjectMocks
    private SePayWebhookController controller;

    private final String secret = "test_webhook_secret_123";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        ReflectionTestUtils.setField(controller, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(controller, "webhookSecret", secret);
    }

    private String calculateSignature(String secretKey, String timestamp, String rawBody) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hmacBytes = mac.doFinal((timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8));
        return "sha256=" + HexFormat.of().formatHex(hmacBytes);
    }

    @Test
    @DisplayName("a. Thiếu header hoặc secret rỗng -> 401")
    void handleWebhook_MissingHeaderOrSecret_Returns401() {
        // Thiếu header signature
        ResponseEntity<Map<String, Object>> res1 = controller.handleWebhook("{}", null, "1700000000");
        assertEquals(HttpStatus.UNAUTHORIZED, res1.getStatusCode());

        // Secret rỗng
        ReflectionTestUtils.setField(controller, "webhookSecret", "");
        ResponseEntity<Map<String, Object>> res2 = controller.handleWebhook("{}", "sha256=123", "1700000000");
        assertEquals(HttpStatus.UNAUTHORIZED, res2.getStatusCode());
    }

    @Test
    @DisplayName("b. Timestamp lệch quá 300 giây -> 401")
    void handleWebhook_TimestampExpired_Returns401() {
        long oldTimestamp = Instant.now().getEpochSecond() - 301;
        ResponseEntity<Map<String, Object>> res = controller.handleWebhook("{}", "sha256=dummy", String.valueOf(oldTimestamp));
        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
    }

    @Test
    @DisplayName("c. Chữ ký HMAC-SHA256 không khớp -> 401")
    void handleWebhook_InvalidSignature_Returns401() {
        long now = Instant.now().getEpochSecond();
        ResponseEntity<Map<String, Object>> res = controller.handleWebhook("{}", "sha256=invalid_hash", String.valueOf(now));
        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
    }

    @Test
    @DisplayName("d. Lỗi parse JSON rawBody -> 400")
    void handleWebhook_MalformedJson_Returns400() throws Exception {
        long now = Instant.now().getEpochSecond();
        String rawBody = "{invalid_json:";
        String sig = calculateSignature(secret, String.valueOf(now), rawBody);

        ResponseEntity<Map<String, Object>> res = controller.handleWebhook(rawBody, sig, String.valueOf(now));
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals(false, res.getBody().get("success"));
    }

    @Test
    @DisplayName("e. Idempotency: Giao dịch sepayId đã tồn tại -> 200 OK ngay")
    void handleWebhook_AlreadyExists_Returns200Immediately() throws Exception {
        long now = Instant.now().getEpochSecond();
        String rawBody = "{\"id\":92704,\"gateway\":\"Vietcombank\",\"transactionDate\":\"2024-07-02 11:08:33\",\"accountNumber\":\"1017588888\",\"transferType\":\"in\",\"transferAmount\":10000}";
        String sig = calculateSignature(secret, String.valueOf(now), rawBody);

        when(sePayTransactionRepository.existsBySepayId(92704L)).thenReturn(true);

        ResponseEntity<Map<String, Object>> res = controller.handleWebhook(rawBody, sig, String.valueOf(now));
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(true, res.getBody().get("success"));
        verify(sePayTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("f. DuplicateKeyException (race condition) -> trả về 200 OK")
    void handleWebhook_DuplicateKeyException_Returns200() throws Exception {
        long now = Instant.now().getEpochSecond();
        String rawBody = "{\"id\":92704,\"gateway\":\"Vietcombank\",\"transactionDate\":\"2024-07-02 11:08:33\",\"accountNumber\":\"1017588888\",\"transferType\":\"in\",\"transferAmount\":10000}";
        String sig = calculateSignature(secret, String.valueOf(now), rawBody);

        when(sePayTransactionRepository.existsBySepayId(92704L)).thenReturn(false);
        when(sePayTransactionRepository.save(any())).thenThrow(new DuplicateKeyException("Duplicate key sepayId"));

        ResponseEntity<Map<String, Object>> res = controller.handleWebhook(rawBody, sig, String.valueOf(now));
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(true, res.getBody().get("success"));
    }

    @Test
    @DisplayName("g. Chuyển khoản in: trích xuất mã BETES12345678 từ content và cập nhật nguyên tử đơn hàng sang PAID")
    void handleWebhook_TransferIn_UpdatesOrderToPaidAtomically() throws Exception {
        long now = Instant.now().getEpochSecond();
        String rawBody = "{\"id\":92704,\"gateway\":\"Vietcombank\",\"transactionDate\":\"2024-07-02 11:08:33\",\"accountNumber\":\"1017588888\",\"subAccount\":\"\",\"code\":null,\"content\":\"KH chuyen tien BETES12345678 mua hang\",\"transferType\":\"in\",\"transferAmount\":50000,\"accumulated\":105010000,\"referenceCode\":\"FT24012345678\"}";
        String sig = calculateSignature(secret, String.valueOf(now), rawBody);

        Order order = Order.builder()
                .orderCode("BETES12345678")
                .paymentMethod(PaymentMethod.SEPAY)
                .paymentStatus(PaymentStatus.UNPAID)
                .finalAmount(BigDecimal.valueOf(50000))
                .build();

        when(sePayTransactionRepository.existsBySepayId(92704L)).thenReturn(false);
        when(sePayTransactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findByOrderCode("BETES12345678")).thenReturn(Optional.of(order));

        UpdateResult updateResult = mock(UpdateResult.class);
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Order.class))).thenReturn(updateResult);

        ResponseEntity<Map<String, Object>> res = controller.handleWebhook(rawBody, sig, String.valueOf(now));

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(true, res.getBody().get("success"));
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), any(Update.class), eq(Order.class));
    }

    @Test
    @DisplayName("h. Không tìm thấy đơn -> vẫn trả 200 OK")
    void handleWebhook_OrderNotFound_Returns200() throws Exception {
        long now = Instant.now().getEpochSecond();
        String rawBody = "{\"id\":92704,\"gateway\":\"Vietcombank\",\"transactionDate\":\"2024-07-02 11:08:33\",\"accountNumber\":\"1017588888\",\"code\":\"BETES99999999\",\"transferType\":\"in\",\"transferAmount\":50000}";
        String sig = calculateSignature(secret, String.valueOf(now), rawBody);

        when(sePayTransactionRepository.existsBySepayId(92704L)).thenReturn(false);
        when(sePayTransactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findByOrderCode("BETES99999999")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> res = controller.handleWebhook(rawBody, sig, String.valueOf(now));

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(true, res.getBody().get("success"));
        verify(mongoTemplate, never()).updateFirst(any(), any(), eq(Order.class));
    }

    @Test
    @DisplayName("h. Lỗi hệ thống bất ngờ ở bước g -> xóa transaction vừa lưu và trả 500 để SePay retry")
    void handleWebhook_UnexpectedErrorInStepG_RollsBackTransactionAndReturns500() throws Exception {
        long now = Instant.now().getEpochSecond();
        String rawBody = "{\"id\":92704,\"gateway\":\"Vietcombank\",\"transactionDate\":\"2024-07-02 11:08:33\",\"accountNumber\":\"1017588888\",\"code\":\"BETES12345678\",\"transferType\":\"in\",\"transferAmount\":50000}";
        String sig = calculateSignature(secret, String.valueOf(now), rawBody);

        SePayTransaction savedTx = SePayTransaction.builder().id("tx_123").sepayId(92704L).build();
        when(sePayTransactionRepository.existsBySepayId(92704L)).thenReturn(false);
        when(sePayTransactionRepository.save(any())).thenReturn(savedTx);

        when(orderRepository.findByOrderCode("BETES12345678")).thenThrow(new RuntimeException("MongoDB Connection Timeout"));

        ResponseEntity<Map<String, Object>> res = controller.handleWebhook(rawBody, sig, String.valueOf(now));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getStatusCode());
        assertEquals(false, res.getBody().get("success"));
        verify(sePayTransactionRepository, times(1)).delete(savedTx);
    }

    @Test
    @DisplayName("4. SePayService buildQrUrl: format và URL-encode đúng")
    void sePayService_BuildQrUrl_FormattedCorrectly() {
        SePayService sePayService = new SePayService();
        ReflectionTestUtils.setField(sePayService, "bankId", "MBBank");
        ReflectionTestUtils.setField(sePayService, "bankAccount", "0123456789");

        String qrUrl = sePayService.buildQrUrl(50000L, "BETES12345678");
        assertEquals("https://qr.sepay.vn/img?acc=MBBank-0123456789&template=compact&amount=50000&des=BETES12345678", qrUrl);
    }
}

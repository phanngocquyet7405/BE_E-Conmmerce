package com.ecommerce.controller;

import com.ecommerce.document.Order;
import com.ecommerce.document.PaymentMethod;
import com.ecommerce.document.PaymentStatus;
import com.ecommerce.document.SePayTransaction;
import com.ecommerce.dto.request.SePayWebhookRequest;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.SePayTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class SePayWebhookController {

    private static final Pattern ORDER_CODE_PATTERN = Pattern.compile("BETES\\d{6,8}", Pattern.CASE_INSENSITIVE);

    private final SePayTransactionRepository sePayTransactionRepository;
    private final OrderRepository orderRepository;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    @Value("${sepay.webhook-secret:}")
    private String webhookSecret;

    @PostMapping("/sepay-webhook")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-SePay-Signature", required = false) String signature,
            @RequestHeader(value = "X-SePay-Timestamp", required = false) String timestampHeader) {

        // a. Thiếu header hoặc secret rỗng -> 401 (fail closed)
        if (!StringUtils.hasText(signature) || !StringUtils.hasText(timestampHeader) || !StringUtils.hasText(webhookSecret)) {
            log.warn("SePay Webhook bị từ chối: Thiếu header xác thực hoặc webhook secret chưa cấu hình.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Unauthorized"));
        }

        // b. Timestamp là Unix GIÂY; |now - timestamp| > 300 -> 401
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            log.warn("SePay Webhook bị từ chối: Timestamp không hợp lệ.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Invalid timestamp"));
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > 300) {
            log.warn("SePay Webhook bị từ chối: Timestamp lệch quá 300 giây so với hiện tại (|{} - {}| > 300).", now, timestamp);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Timestamp expired"));
        }

        // c. expected = "sha256=" + hex(HMAC-SHA256(secret, timestamp + "." + rawBody));
        // So sánh constant-time (MessageDigest.isEqual); không khớp -> 401. Dùng rawBody gốc.
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);

            String payloadToSign = timestampHeader.trim() + "." + rawBody;
            byte[] hmacBytes = mac.doFinal(payloadToSign.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = "sha256=" + HexFormat.of().formatHex(hmacBytes);

            byte[] expectedBytes = expectedSignature.toLowerCase().getBytes(StandardCharsets.UTF_8);
            byte[] actualBytes = signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);

            if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
                log.warn("SePay Webhook bị từ chối: Chữ ký HMAC không khớp.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Invalid signature"));
            }
        } catch (Exception ex) {
            log.error("Lỗi khi kiểm tra chữ ký SePay Webhook: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Signature verification failed"));
        }

        // d. Parse rawBody bằng ObjectMapper -> SePayWebhookRequest; lỗi parse -> 400
        SePayWebhookRequest request;
        try {
            request = objectMapper.readValue(rawBody, SePayWebhookRequest.class);
        } catch (Exception ex) {
            log.error("Lỗi parse SePay Webhook rawBody: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Invalid JSON body"));
        }

        // e. Idempotency: existsBySepayId(id) -> trả 200 {"success":true} ngay
        if (request.getId() != null && sePayTransactionRepository.existsBySepayId(request.getId())) {
            log.info("SePay Webhook: Giao dịch SePay ID {} đã tồn tại, trả về 200 OK ngay.", request.getId());
            return ResponseEntity.ok(Map.of("success", true));
        }

        // f. Lưu SePayTransaction (kèm rawBody); bắt DuplicateKeyException (race) -> 200
        long transferAmount = request.getTransferAmount() != null ? request.getTransferAmount() : 0L;
        long amountIn = "in".equalsIgnoreCase(request.getTransferType()) ? transferAmount : 0L;
        long amountOut = "in".equalsIgnoreCase(request.getTransferType()) ? 0L : transferAmount;

        SePayTransaction transaction = SePayTransaction.builder()
                .sepayId(request.getId())
                .gateway(request.getGateway())
                .transactionDate(request.getTransactionDate())
                .accountNumber(request.getAccountNumber())
                .subAccount(request.getSubAccount())
                .code(request.getCode())
                .amountIn(amountIn)
                .amountOut(amountOut)
                .accumulated(request.getAccumulated())
                .content(request.getContent())
                .referenceCode(request.getReferenceCode())
                .rawBody(rawBody)
                .createdAt(LocalDateTime.now())
                .build();

        SePayTransaction savedTransaction;
        try {
            savedTransaction = sePayTransactionRepository.save(transaction);
        } catch (DuplicateKeyException ex) {
            log.info("SePay Webhook: Trùng lặp khóa sepayId {} (race condition). Bỏ qua và trả 200.", request.getId());
            return ResponseEntity.ok(Map.of("success", true));
        }

        // g. Xử lý thanh toán đơn hàng khi transferType == "in"
        try {
            if ("in".equalsIgnoreCase(request.getTransferType())) {
                // Lấy mã đơn từ `code`, nếu null thì regex BETES\d{6,8} trên `content`
                String orderCode = request.getCode();
                if (!StringUtils.hasText(orderCode) && StringUtils.hasText(request.getContent())) {
                    Matcher matcher = ORDER_CODE_PATTERN.matcher(request.getContent());
                    if (matcher.find()) {
                        orderCode = matcher.group();
                    }
                }

                if (StringUtils.hasText(orderCode)) {
                    Optional<Order> orderOpt = orderRepository.findByOrderCode(orderCode);
                    if (orderOpt.isPresent()) {
                        Order order = orderOpt.get();
                        BigDecimal paidAmount = BigDecimal.valueOf(transferAmount);

                        // Điều kiện: paymentMethod == SEPAY, paymentStatus == UNPAID, transferAmount >= finalAmount
                        if (order.getPaymentMethod() == PaymentMethod.SEPAY
                                && order.getPaymentStatus() == PaymentStatus.UNPAID
                                && paidAmount.compareTo(order.getFinalAmount()) >= 0) {

                            // Cập nhật nguyên tử (điều kiện paymentStatus == UNPAID) để tránh trả hai lần
                            Query query = new Query(Criteria.where("orderCode").is(order.getOrderCode())
                                    .and("paymentStatus").is(PaymentStatus.UNPAID));
                            Update update = new Update()
                                    .set("paymentStatus", PaymentStatus.PAID)
                                    .set("paidAt", Instant.now())
                                    .set("sepayTransactionId", String.valueOf(request.getId()))
                                    .set("updatedAt", Instant.now());

                            UpdateResult updateResult = mongoTemplate.updateFirst(query, update, Order.class);
                            if (updateResult.getModifiedCount() > 0) {
                                log.info("SePay Webhook: Cập nhật thành công PAID cho đơn hàng: {}", order.getOrderCode());
                            } else {
                                log.warn("SePay Webhook: Đơn hàng {} không thể cập nhật (có thể đã được xử lý bởi luồng đồng thời).", order.getOrderCode());
                            }
                        } else {
                            // h. Không khớp điều kiện -> vẫn trả 200 {"success":true} và log warn
                            log.warn("SePay Webhook: Đơn hàng {} không khớp điều kiện thanh toán. Method={}, Status={}, Paid={}, FinalAmount={}",
                                    order.getOrderCode(), order.getPaymentMethod(), order.getPaymentStatus(), paidAmount, order.getFinalAmount());
                        }
                    } else {
                        // h. Không tìm thấy đơn -> vẫn trả 200 {"success":true} và log warn
                        log.warn("SePay Webhook: Không tìm thấy đơn hàng với mã: {}", orderCode);
                    }
                } else {
                    log.warn("SePay Webhook: Không trích xuất được mã đơn hàng BETES từ SePay ID {}", request.getId());
                }
            }

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception ex) {
            // Lỗi hệ thống bất ngờ ở bước g -> xóa transaction vừa lưu rồi trả 500 để SePay retry
            log.error("SePay Webhook: Lỗi hệ thống bất ngờ ở bước g cho SePay ID {}: {}", request.getId(), ex.getMessage(), ex);
            try {
                sePayTransactionRepository.delete(savedTransaction);
            } catch (Exception rollbackEx) {
                log.error("SePay Webhook: Rollback xóa transaction thất bại: {}", rollbackEx.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Internal server error"));
        }
    }
}

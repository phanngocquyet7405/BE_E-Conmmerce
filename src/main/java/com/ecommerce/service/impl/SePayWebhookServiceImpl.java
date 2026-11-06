package com.ecommerce.service.impl;

import com.ecommerce.config.SePayProperties;
import com.ecommerce.document.*;
import com.ecommerce.dto.request.SePayWebhookRequest;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.SePayTransactionRepository;
import com.ecommerce.service.SePayWebhookService;
import com.ecommerce.service.dto.WebhookResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SePayWebhookServiceImpl implements SePayWebhookService {

    private final SePayTransactionRepository sePayTransactionRepository;
    private final OrderRepository orderRepository;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final SePayProperties sePayProperties;

    @Override
    public WebhookResult processWebhook(String rawBody, String authorization, String signature, String timestampHeader) {
        // 1. Xác thực danh tính Webhook (API Key hoặc HMAC-SHA256)
        boolean authenticated = false;

        // Cơ chế 1: Xác thực qua Authorization: Apikey <key>
        if (StringUtils.hasText(authorization)) {
            String trimmedAuth = authorization.trim();
            String prefix = "apikey ";
            if (trimmedAuth.toLowerCase().startsWith(prefix)) {
                String providedApiKey = trimmedAuth.substring(prefix.length()).trim();
                String configuredApiKey = sePayProperties.getApiKey();
                String configuredSecret = sePayProperties.getWebhookSecret();

                if (StringUtils.hasText(configuredApiKey) && isConstantTimeEqual(configuredApiKey, providedApiKey)) {
                    authenticated = true;
                } else if (StringUtils.hasText(configuredSecret) && isConstantTimeEqual(configuredSecret, providedApiKey)) {
                    authenticated = true;
                }
            }
        }

        // Cơ chế 2: Xác thực qua HMAC-SHA256 (X-SePay-Signature và X-SePay-Timestamp)
        if (!authenticated && StringUtils.hasText(signature) && StringUtils.hasText(timestampHeader)) {
            String secret = StringUtils.hasText(sePayProperties.getWebhookSecret())
                    ? sePayProperties.getWebhookSecret()
                    : sePayProperties.getApiKey();

            if (StringUtils.hasText(secret)) {
                long timestamp;
                try {
                    timestamp = Long.parseLong(timestampHeader.trim());
                } catch (NumberFormatException e) {
                    log.warn("SePay Webhook bị từ chối: Timestamp không hợp lệ: {}", timestampHeader);
                    return WebhookResult.unauthorized("Invalid timestamp");
                }

                long now = Instant.now().getEpochSecond();
                if (Math.abs(now - timestamp) > 300) {
                    log.warn("SePay Webhook bị từ chối: Timestamp lệch quá 300 giây (|{} - {}| > 300).", now, timestamp);
                    return WebhookResult.unauthorized("Timestamp expired");
                }

                try {
                    Mac mac = Mac.getInstance("HmacSHA256");
                    SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
                    mac.init(secretKey);

                    String payloadToSign = timestampHeader.trim() + "." + rawBody;
                    byte[] hmacBytes = mac.doFinal(payloadToSign.getBytes(StandardCharsets.UTF_8));
                    String expectedSignature = "sha256=" + HexFormat.of().formatHex(hmacBytes);

                    if (isConstantTimeEqual(expectedSignature.toLowerCase(), signature.trim().toLowerCase())) {
                        authenticated = true;
                    }
                } catch (Exception ex) {
                    log.error("Lỗi khi tính toán HMAC SePay Webhook: {}", ex.getMessage());
                }
            }
        }

        // Nếu cả hai cơ chế đều không vượt qua -> Từ chối 401 (Fail-Closed)
        if (!authenticated) {
            log.warn("SePay Webhook bị từ chối: Xác thực không thành công (thiếu hoặc sai header/key).");
            return WebhookResult.unauthorized("Unauthorized");
        }

        // 2. Parse rawBody bằng ObjectMapper
        SePayWebhookRequest request;
        try {
            request = objectMapper.readValue(rawBody, SePayWebhookRequest.class);
        } catch (Exception ex) {
            log.error("Lỗi parse SePay Webhook JSON rawBody: {}", ex.getMessage());
            return WebhookResult.badRequest("Invalid JSON body");
        }

        // 3. Kiểm tra Idempotency
        if (request.getId() != null) {
            Optional<SePayTransaction> existingOpt = sePayTransactionRepository.findBySepayId(request.getId());
            if (existingOpt.isPresent()) {
                SePayTransaction existing = existingOpt.get();
                if (existing.getStatus() == SePayTransactionStatus.PROCESSED) {
                    log.info("SePay Webhook: Giao dịch SePay ID {} đã xử lý hoàn tất (PROCESSED). Trả 200 OK ngay.", request.getId());
                    return WebhookResult.ok();
                }
                if (existing.getStatus() == SePayTransactionStatus.IGNORED) {
                    log.info("SePay Webhook: Giao dịch SePay ID {} đã được ghi nhận IGNORED. Trả 200 OK ngay.", request.getId());
                    return WebhookResult.ok();
                }
            }
        }

        // 4. Lưu SePayTransaction với trạng thái RECEIVED
        long transferAmount = request.getTransferAmount() != null ? request.getTransferAmount() : 0L;
        long amountIn = "in".equalsIgnoreCase(request.getTransferType()) ? transferAmount : 0L;
        long amountOut = "in".equalsIgnoreCase(request.getTransferType()) ? 0L : transferAmount;

        SePayTransaction transaction = (request.getId() != null)
                ? sePayTransactionRepository.findBySepayId(request.getId()).orElse(null)
                : null;

        if (transaction == null) {
            transaction = SePayTransaction.builder()
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
                    .status(SePayTransactionStatus.RECEIVED)
                    .createdAt(LocalDateTime.now())
                    .build();

            try {
                transaction = sePayTransactionRepository.save(transaction);
            } catch (DuplicateKeyException ex) {
                log.info("SePay Webhook: Trùng lặp khóa sepayId {} (race condition). Bỏ qua và trả 200 OK.", request.getId());
                return WebhookResult.ok();
            }
        }

        try {
            // 5. Kiểm tra loại giao dịch: Chỉ xử lý gạch nợ cho tiền vào ("in")
            if (!"in".equalsIgnoreCase(request.getTransferType())) {
                log.info("SePay Webhook: Giao dịch SePay ID {} là tiền ra (transferType={}), bỏ qua không gạch nợ.",
                        request.getId(), request.getTransferType());
                transaction.setStatus(SePayTransactionStatus.IGNORED);
                transaction.setFailureReason("Loại giao dịch không phải tiền vào (transferType=" + request.getTransferType() + ")");
                sePayTransactionRepository.save(transaction);
                return WebhookResult.ok();
            }

            // 6. Đối chiếu số tài khoản thụ hưởng của cửa hàng
            String configuredBankAccount = sePayProperties.getBankAccount();
            if (StringUtils.hasText(request.getAccountNumber()) && StringUtils.hasText(configuredBankAccount)) {
                if (!request.getAccountNumber().trim().equalsIgnoreCase(configuredBankAccount.trim())) {
                    log.warn("SePay Webhook: Số tài khoản nhận ({}) không khớp tài khoản cấu hình ({}). SePay ID: {}",
                            request.getAccountNumber(), configuredBankAccount, request.getId());
                    transaction.setStatus(SePayTransactionStatus.MISMATCH);
                    transaction.setFailureReason("Số tài khoản nhận không khớp: " + request.getAccountNumber() + " vs " + configuredBankAccount);
                    sePayTransactionRepository.save(transaction);
                    return WebhookResult.ok();
                }
            }

            // 7. Trích xuất mã đơn hàng
            String orderCodePrefix = StringUtils.hasText(sePayProperties.getOrderCodePrefix())
                    ? sePayProperties.getOrderCodePrefix().trim()
                    : "BETES";
            Pattern orderCodePattern = Pattern.compile(Pattern.quote(orderCodePrefix) + "\\d{6,8}", Pattern.CASE_INSENSITIVE);

            String orderCode = request.getCode();
            if (!StringUtils.hasText(orderCode) && StringUtils.hasText(request.getContent())) {
                Matcher matcher = orderCodePattern.matcher(request.getContent());
                if (matcher.find()) {
                    orderCode = matcher.group();
                }
            }

            if (!StringUtils.hasText(orderCode)) {
                log.warn("SePay Webhook: Không trích xuất được mã đơn hàng tiền tố {} từ SePay ID {}",
                        orderCodePrefix, request.getId());
                transaction.setStatus(SePayTransactionStatus.MISMATCH);
                transaction.setFailureReason("Không trích xuất được mã đơn hàng từ nội dung giao dịch");
                sePayTransactionRepository.save(transaction);
                return WebhookResult.ok();
            }

            orderCode = orderCode.trim().toUpperCase();
            transaction.setOrderCode(orderCode);

            // 8. Tìm đơn hàng tương ứng
            Optional<Order> orderOpt = orderRepository.findByOrderCode(orderCode);
            if (orderOpt.isEmpty()) {
                log.warn("SePay Webhook: Không tìm thấy đơn hàng với mã: {}", orderCode);
                transaction.setStatus(SePayTransactionStatus.MISMATCH);
                transaction.setFailureReason("Không tìm thấy đơn hàng với mã: " + orderCode);
                sePayTransactionRepository.save(transaction);
                return WebhookResult.ok();
            }

            Order order = orderOpt.get();
            transaction.setOrderId(order.getId());

            // 9. Kiểm tra trạng thái đơn hàng
            if (order.getStatus() == OrderStatus.CANCELLED) {
                log.warn("SePay Webhook: Đơn hàng {} đã bị hủy (CANCELLED). Cần hỗ trợ hoàn tiền.", orderCode);
                transaction.setStatus(SePayTransactionStatus.MISMATCH);
                transaction.setFailureReason("Đơn hàng đã bị hủy, cần hỗ trợ hoàn tiền cho khách");
                sePayTransactionRepository.save(transaction);
                return WebhookResult.ok();
            }

            if (order.getPaymentStatus() == PaymentStatus.PAID) {
                log.info("SePay Webhook: Đơn hàng {} đã ở trạng thái PAID trước đó.", orderCode);
                transaction.setStatus(SePayTransactionStatus.PROCESSED);
                sePayTransactionRepository.save(transaction);
                return WebhookResult.ok();
            }

            if (order.getPaymentMethod() != PaymentMethod.SEPAY && order.getPaymentMethod() != PaymentMethod.BANKING) {
                log.warn("SePay Webhook: Đơn hàng {} có phương thức {} không hỗ trợ SePay.", orderCode, order.getPaymentMethod());
                transaction.setStatus(SePayTransactionStatus.MISMATCH);
                transaction.setFailureReason("Phương thức thanh toán không hỗ trợ: " + order.getPaymentMethod());
                sePayTransactionRepository.save(transaction);
                return WebhookResult.ok();
            }

            // 10. Kiểm tra số tiền chuyển khoản
            BigDecimal paidAmount = BigDecimal.valueOf(transferAmount);
            BigDecimal finalAmount = order.getFinalAmount() != null ? order.getFinalAmount() : BigDecimal.ZERO;
            BigDecimal diff = paidAmount.subtract(finalAmount);

            if (paidAmount.compareTo(finalAmount) < 0) {
                // Khách chuyển thiếu tiền (Partial / Underpaid)
                log.warn("SePay Webhook: Khách chuyển thiếu tiền cho đơn {}. Chuyển={}, Cần={}", orderCode, paidAmount, finalAmount);
                order.setPaidAmount(paidAmount);
                order.setAmountDiscrepancy(diff);
                order.setIsUnderpaid(true);
                orderRepository.save(order);

                transaction.setStatus(SePayTransactionStatus.MISMATCH);
                transaction.setAmountDiscrepancy(diff.longValue());
                transaction.setFailureReason("Khách chuyển thiếu tiền: " + paidAmount + "/" + finalAmount);
                sePayTransactionRepository.save(transaction);
                return WebhookResult.ok();
            }

            // Khách chuyển đủ hoặc thừa tiền (Paid / Overpaid)
            boolean isOverpaid = diff.compareTo(BigDecimal.ZERO) > 0;
            Query query = new Query(Criteria.where("orderCode").is(order.getOrderCode())
                    .and("paymentStatus").is(PaymentStatus.UNPAID));
            Update update = new Update()
                    .set("paymentStatus", PaymentStatus.PAID)
                    .set("status", OrderStatus.CONFIRMED)
                    .set("paidAt", Instant.now())
                    .set("paidAmount", paidAmount)
                    .set("amountDiscrepancy", diff)
                    .set("isOverpaid", isOverpaid)
                    .set("isUnderpaid", false)
                    .set("sepayTransactionId", String.valueOf(request.getId()))
                    .set("updatedAt", Instant.now());

            UpdateResult updateResult = mongoTemplate.updateFirst(query, update, Order.class);
            if (updateResult.getModifiedCount() > 0) {
                log.info("SePay Webhook: Cập nhật thành công PAID và CONFIRMED cho đơn: {}, isOverpaid={}, diff={}",
                        orderCode, isOverpaid, diff);
            } else {
                log.warn("SePay Webhook: Đơn hàng {} không thể cập nhật (có thể đã được xử lý bởi luồng khác).", orderCode);
            }

            transaction.setStatus(SePayTransactionStatus.PROCESSED);
            transaction.setAmountDiscrepancy(diff.longValue());
            sePayTransactionRepository.save(transaction);

            return WebhookResult.ok();

        } catch (Exception ex) {
            log.error("SePay Webhook: Lỗi hệ thống bất ngờ khi xử lý SePay ID {}: {}", request.getId(), ex.getMessage(), ex);
            try {
                transaction.setStatus(SePayTransactionStatus.FAILED);
                transaction.setFailureReason(ex.getMessage());
                sePayTransactionRepository.save(transaction);
            } catch (Exception saveEx) {
                log.error("Không thể cập nhật trạng thái FAILED cho transaction {}: {}", transaction.getId(), saveEx.getMessage());
            }
            return WebhookResult.internalError("Internal server error");
        }
    }

    private boolean isConstantTimeEqual(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}

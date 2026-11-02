package com.ecommerce.service.impl;

import com.ecommerce.config.AppProperties;
import com.ecommerce.document.Order;
import com.ecommerce.document.OrderStatus;
import com.ecommerce.document.PaymentMethod;
import com.ecommerce.document.PaymentStatus;
import com.ecommerce.dto.request.SePayWebhookRequest;
import com.ecommerce.dto.response.PaymentQrResponse;
import com.ecommerce.dto.response.PaymentStatusResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final AppProperties appProperties;

    @Override
    public PaymentQrResponse getPaymentQrInfo(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        String accNumber = appProperties.getSepay().getAccountNumber();
        String bank = appProperties.getSepay().getBankName();
        String holder = appProperties.getSepay().getAccountHolder();
        String template = appProperties.getSepay().getQrTemplate();

        String transferContent = order.getOrderCode();
        BigDecimal amount = order.getFinalAmount();

        // Định dạng VietQR SePay: https://qr.sepay.vn/img?acc=...&bank=...&amount=...&des=...
        String encodedContent = URLEncoder.encode(transferContent, StandardCharsets.UTF_8);
        String qrImageUrl = String.format(
                "https://qr.sepay.vn/img?acc=%s&bank=%s&amount=%s&des=%s&template=%s",
                accNumber, bank, amount.toBigInteger().toString(), encodedContent, template
        );

        String instruction = String.format(
                "Vui lòng quét mã QR hoặc chuyển khoản chính xác số tiền %s đ vào tài khoản %s (%s) với nội dung: %s",
                amount.toBigInteger(), accNumber, bank, transferContent
        );

        return PaymentQrResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .amount(amount)
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .bankName(bank)
                .accountNumber(accNumber)
                .accountHolder(holder)
                .transferContent(transferContent)
                .qrImageUrl(qrImageUrl)
                .paymentInstruction(instruction)
                .build();
    }

    @Override
    public PaymentStatusResponse checkPaymentStatus(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        return PaymentStatusResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .paymentStatus(order.getPaymentStatus())
                .orderStatus(order.getStatus())
                .paidAt(order.getPaidAt())
                .sepayTransactionId(order.getSepayTransactionId())
                .build();
    }

    @Override
    public void processSepayWebhook(String authHeader, SePayWebhookRequest request) {
        log.info("Nhận Webhook từ SePay: id={}, transferAmount={}, content={}, referenceCode={}",
                request.getId(), request.getTransferAmount(), request.getContent(), request.getReferenceCode());

        // 1. Xác thực API Key nếu được cấu hình
        String configuredApiKey = appProperties.getSepay().getApiKey();
        if (StringUtils.hasText(configuredApiKey)) {
            String expectedBearer = "Apikey " + configuredApiKey;
            if (!expectedBearer.equals(authHeader) && !configuredApiKey.equals(authHeader)) {
                log.warn("SePay Webhook từ chối: Sai API Key trong header Authorization");
                throw new AppException(ErrorCode.ACCESS_DENIED, "Chữ ký SePay Webhook không hợp lệ");
            }
        }

        // 2. Trích xuất mã đơn hàng từ nội dung chuyển khoản (content hoặc description)
        String content = request.getContent() != null ? request.getContent() : request.getDescription();
        if (!StringUtils.hasText(content)) {
            log.warn("Nội dung chuyển khoản SePay rỗng, bỏ qua");
            return;
        }

        // Tìm kiếm chuỗi ORD- trong content
        String matchedOrderCode = null;
        for (String word : content.replaceAll("[^a-zA-Z0-9-]", " ").split("\\s+")) {
            if (word.toUpperCase().startsWith("ORD-")) {
                matchedOrderCode = word.trim().toUpperCase();
                break;
            }
        }

        if (matchedOrderCode == null) {
            log.warn("Không tìm thấy mã đơn hàng ORD- trong nội dung: {}", content);
            return;
        }

        Order order = orderRepository.findByOrderCode(matchedOrderCode).orElse(null);
        if (order == null) {
            log.warn("Đơn hàng với mã {} không tồn tại trong hệ thống", matchedOrderCode);
            return;
        }

        // 3. Đối soát số tiền thanh toán
        if (request.getTransferAmount() != null && BigDecimal.valueOf(request.getTransferAmount()).compareTo(order.getFinalAmount()) >= 0) {
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setPaidAt(Instant.now());
            order.setSepayTransactionId(String.valueOf(request.getId()));
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.CONFIRMED);
            }
            orderRepository.save(order);
            log.info("==> Thanh toán SePay THÀNH CÔNG cho đơn hàng: {}. Đã chuyển sang PAID/CONFIRMED", matchedOrderCode);
        } else {
            log.warn("Số tiền chuyển khoản ({}) nhỏ hơn tổng tiền đơn hàng ({}) cho đơn {}",
                    request.getTransferAmount(), order.getFinalAmount(), matchedOrderCode);
        }
    }
}

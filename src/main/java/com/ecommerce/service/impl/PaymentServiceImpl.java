package com.ecommerce.service.impl;

import com.ecommerce.common.response.PageResponse;
import com.ecommerce.document.Order;
import com.ecommerce.document.PaymentMethod;
import com.ecommerce.document.SePayTransaction;
import com.ecommerce.document.SePayTransactionStatus;
import com.ecommerce.dto.request.AdminProcessTransactionRequest;
import com.ecommerce.dto.response.PaymentMethodResponse;
import com.ecommerce.dto.response.PaymentQrResponse;
import com.ecommerce.dto.response.PaymentStatusResponse;
import com.ecommerce.dto.response.SePayTransactionResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.SePayTransactionRepository;
import com.ecommerce.service.PaymentService;
import com.ecommerce.service.SePayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final SePayService sePayService;
    private final SePayTransactionRepository sePayTransactionRepository;

    @Override
    public PaymentQrResponse getPaymentQrInfo(String userId, boolean isAdmin, String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        // Phòng chống IDOR: Nếu không phải Admin và không phải chủ đơn hàng thì ném ORDER_NOT_FOUND
        if (!isAdmin && (userId == null || !order.getUserId().equals(userId))) {
            log.warn("Cảnh báo IDOR: User {} cố gắng truy cập thông tin thanh toán của đơn hàng {} (thuộc về {})",
                    userId, orderId, order.getUserId());
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }

        String accNumber = sePayService.getBankAccount();
        String bank = sePayService.getBankId();
        String holder = sePayService.getAccountHolder();

        String transferContent = order.getOrderCode();
        BigDecimal amount = order.getFinalAmount();

        // Sử dụng SePayService định dạng chuẩn VietQR SePay:
        // https://qr.sepay.vn/img?acc={bank-id}-{bank-account}&template=compact&amount={amount}&des={orderCode}
        long amountValue = amount != null ? amount.longValue() : 0L;
        String qrImageUrl = sePayService.buildQrUrl(amountValue, transferContent);

        String instruction = String.format(
                "Vui lòng quét mã QR hoặc chuyển khoản chính xác số tiền %s đ vào tài khoản %s (%s) với nội dung: %s",
                amount != null ? amount.toBigInteger() : 0, accNumber, bank, transferContent
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
    public PaymentStatusResponse checkPaymentStatus(String userId, boolean isAdmin, String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        // Phòng chống IDOR: Nếu không phải Admin và không phải chủ đơn hàng thì ném ORDER_NOT_FOUND
        if (!isAdmin && (userId == null || !order.getUserId().equals(userId))) {
            log.warn("Cảnh báo IDOR: User {} cố gắng kiểm tra trạng thái thanh toán của đơn hàng {} (thuộc về {})",
                    userId, orderId, order.getUserId());
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }

        return PaymentStatusResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .paymentStatus(order.getPaymentStatus())
                .orderStatus(order.getStatus())
                .paidAt(order.getPaidAt())
                .sepayTransactionId(order.getSepayTransactionId())
                .finalAmount(order.getFinalAmount() != null ? order.getFinalAmount() : BigDecimal.ZERO)
                .paidAmount(order.getPaidAmount() != null ? order.getPaidAmount() : BigDecimal.ZERO)
                .amountDiscrepancy(order.getAmountDiscrepancy() != null ? order.getAmountDiscrepancy() : BigDecimal.ZERO)
                .isOverpaid(Boolean.TRUE.equals(order.getIsOverpaid()))
                .isUnderpaid(Boolean.TRUE.equals(order.getIsUnderpaid()))
                .build();
    }

    @Override
    public List<PaymentMethodResponse> getAvailablePaymentMethods() {
        return List.of(
                PaymentMethodResponse.builder()
                        .code(PaymentMethod.COD)
                        .name("Thanh toán khi nhận hàng (COD)")
                        .enabled(true)
                        .description("Thanh toán tiền mặt cho shipper khi nhận hàng.")
                        .condition("Áp dụng cho mọi đơn hàng.")
                        .build(),
                PaymentMethodResponse.builder()
                        .code(PaymentMethod.SEPAY)
                        .name("Chuyển khoản VietQR qua SePay")
                        .enabled(true)
                        .description("Quét mã QR VietQR chuyển khoản tự động xác nhận tức thì qua SePay.")
                        .condition("Thời hạn giữ đơn và chuyển khoản là 15 phút.")
                        .build()
        );
    }

    @Override
    public PageResponse<SePayTransactionResponse> getTransactionsForAdmin(SePayTransactionStatus status, Pageable pageable) {
        int safePage = Math.max(pageable.getPageNumber(), 0);
        int safeSize = Math.min(Math.max(pageable.getPageSize(), 1), 50);
        Pageable safePageable = PageRequest.of(safePage, safeSize, pageable.getSort());
        Page<SePayTransaction> page = (status != null)
                ? sePayTransactionRepository.findByStatusOrderByCreatedAtDesc(status, safePageable)
                : sePayTransactionRepository.findAllByOrderByCreatedAtDesc(safePageable);
        return PageResponse.fromPage(page.map(SePayTransactionResponse::fromEntity));
    }

    @Override
    public SePayTransactionResponse processTransactionForAdmin(String adminEmail, String transactionId, AdminProcessTransactionRequest request) {
        SePayTransaction tx = sePayTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không tìm thấy giao dịch với ID: " + transactionId));

        tx.setAdminProcessed(true);
        tx.setProcessedBy(adminEmail != null ? adminEmail : "ADMIN");
        tx.setProcessedAt(LocalDateTime.now());
        tx.setAdminNote(request.getNote());

        if ("REFUNDED".equalsIgnoreCase(request.getAction())) {
            tx.setStatus(SePayTransactionStatus.PROCESSED);
        } else if ("RESOLVED".equalsIgnoreCase(request.getAction())) {
            tx.setStatus(SePayTransactionStatus.PROCESSED);
        }

        SePayTransaction saved = sePayTransactionRepository.save(tx);
        log.info("Admin {} đã xử lý giao dịch SePay {}: action={}, note={}", adminEmail, tx.getSepayId(), request.getAction(), request.getNote());
        return SePayTransactionResponse.fromEntity(saved);
    }
}

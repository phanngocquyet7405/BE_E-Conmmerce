package com.ecommerce.service.impl;

import com.ecommerce.document.Order;
import com.ecommerce.dto.response.PaymentQrResponse;
import com.ecommerce.dto.response.PaymentStatusResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.service.PaymentService;
import com.ecommerce.service.SePayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final SePayService sePayService;

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
                .build();
    }

    @Override
    public java.util.List<com.ecommerce.dto.response.PaymentMethodResponse> getAvailablePaymentMethods() {
        return java.util.List.of(
                com.ecommerce.dto.response.PaymentMethodResponse.builder()
                        .code(com.ecommerce.document.PaymentMethod.COD)
                        .name("Thanh toán khi nhận hàng (COD)")
                        .enabled(true)
                        .description("Thanh toán tiền mặt cho shipper khi nhận hàng.")
                        .condition("Áp dụng cho mọi đơn hàng.")
                        .build(),
                com.ecommerce.dto.response.PaymentMethodResponse.builder()
                        .code(com.ecommerce.document.PaymentMethod.SEPAY)
                        .name("Chuyển khoản VietQR qua SePay")
                        .enabled(true)
                        .description("Quét mã QR VietQR chuyển khoản tự động xác nhận tức thì qua SePay.")
                        .condition("Thời hạn giữ đơn và chuyển khoản là 15 phút.")
                        .build()
        );
    }
}

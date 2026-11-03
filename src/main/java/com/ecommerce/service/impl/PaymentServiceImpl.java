package com.ecommerce.service.impl;

import com.ecommerce.config.AppProperties;
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
    private final AppProperties appProperties;
    private final SePayService sePayService;

    @Override
    public PaymentQrResponse getPaymentQrInfo(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        String accNumber = sePayService.getBankAccount();
        String bank = sePayService.getBankId();
        String holder = (appProperties != null && appProperties.getSepay() != null)
                ? appProperties.getSepay().getAccountHolder()
                : null;

        String transferContent = order.getOrderCode();
        BigDecimal amount = order.getFinalAmount();

        // Sử dụng SePayService định dạng chuẩn VietQR SePay:
        // https://qr.sepay.vn/img?acc={bank-id}-{bank-account}&template=compact&amount={amount}&des={orderCode}
        String qrImageUrl = sePayService.buildQrUrl(amount.longValue(), transferContent);

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
}

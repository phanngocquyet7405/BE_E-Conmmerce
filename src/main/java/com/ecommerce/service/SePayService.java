package com.ecommerce.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class SePayService {

    @Value("${sepay.bank-id:}")
    private String bankId;

    @Value("${sepay.bank-account:}")
    private String bankAccount;

    /**
     * Tạo đường dẫn ảnh mã VietQR SePay theo định dạng chuẩn:
     * https://qr.sepay.vn/img?acc={bank-id}-{bank-account}&template=compact&amount={amount}&des={orderCode}
     *
     * @param amount    Số tiền cần thanh toán
     * @param orderCode Mã đơn hàng (nội dung chuyển khoản, vd: BETES12345678)
     * @return URL ảnh VietQR
     */
    public String buildQrUrl(long amount, String orderCode) {
        String encodedDes = URLEncoder.encode(orderCode, StandardCharsets.UTF_8);
        return String.format(
                "https://qr.sepay.vn/img?acc=%s-%s&template=compact&amount=%d&des=%s",
                bankId,
                bankAccount,
                amount,
                encodedDes
        );
    }
}

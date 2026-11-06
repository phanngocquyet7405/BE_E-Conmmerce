package com.ecommerce.service;

import com.ecommerce.config.SePayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class SePayService {

    private final SePayProperties sePayProperties;

    public String getBankId() {
        return sePayProperties != null ? sePayProperties.getBankId() : null;
    }

    public String getBankAccount() {
        return sePayProperties != null ? sePayProperties.getBankAccount() : null;
    }

    public String getAccountHolder() {
        return sePayProperties != null ? sePayProperties.getAccountHolder() : null;
    }

    public String getOrderCodePrefix() {
        if (sePayProperties != null && StringUtils.hasText(sePayProperties.getOrderCodePrefix())) {
            return sePayProperties.getOrderCodePrefix();
        }
        return "BETES";
    }

    /**
     * Tạo đường dẫn ảnh mã VietQR SePay theo định dạng chuẩn:
     * https://qr.sepay.vn/img?acc={bank-id}-{bank-account}&template=compact&amount={amount}&des={orderCode}
     *
     * @param amount    Số tiền cần thanh toán
     * @param orderCode Mã đơn hàng (nội dung chuyển khoản, vd: BETES12345678)
     * @return URL ảnh VietQR
     */
    public String buildQrUrl(long amount, String orderCode) {
        String encodedDes = URLEncoder.encode(orderCode != null ? orderCode : "", StandardCharsets.UTF_8);
        String template = (sePayProperties != null && StringUtils.hasText(sePayProperties.getQrTemplate()))
                ? sePayProperties.getQrTemplate()
                : "compact";
        String bankId = getBankId();
        String bankAccount = getBankAccount();

        return String.format(
                "https://qr.sepay.vn/img?acc=%s-%s&template=%s&amount=%d&des=%s",
                bankId != null ? bankId : "",
                bankAccount != null ? bankAccount : "",
                template,
                amount,
                encodedDes
        );
    }
}

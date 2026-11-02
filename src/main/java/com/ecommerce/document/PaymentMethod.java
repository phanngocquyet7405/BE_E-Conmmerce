package com.ecommerce.document;

public enum PaymentMethod {
    COD,        // Thanh toán tiền mặt khi nhận hàng
    SEPAY,      // Cổng thanh toán chuyển khoản tự động SePay (VietQR)
    VNPAY,      // Ví VNPay
    MOMO,       // Ví MoMo
    BANKING     // Chuyển khoản ngân hàng thủ công
}

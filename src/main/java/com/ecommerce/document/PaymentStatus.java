package com.ecommerce.document;

public enum PaymentStatus {
    UNPAID,     // Chưa thanh toán
    PAID,       // Đã thanh toán thành công
    FAILED,     // Thanh toán thất bại
    REFUNDED    // Đã hoàn tiền
}

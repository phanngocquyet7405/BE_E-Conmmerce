package com.ecommerce.document;

public enum OrderStatus {
    PENDING,        // Chờ xác nhận
    CONFIRMED,      // Đã xác nhận
    SHIPPING,       // Đang giao hàng
    DELIVERED,      // Đã giao thành công
    CANCELLED,      // Đã hủy
    AWAITING_REFUND // Chờ hoàn tiền (đơn đã thanh toán bị hủy, chờ admin hoàn tiền thủ công)
}

package com.ecommerce.document;

public enum SePayTransactionStatus {
    RECEIVED,    // Đã nhận webhook từ SePay và lưu audit log
    PROCESSED,   // Đã đối soát và gạch nợ thành công cho đơn hàng
    PARTIAL_PAID,// Đã nhận tiền một phần nhưng chưa đủ (chuyển thiếu tiền, chờ chuyển bù)
    FAILED,      // Lỗi hệ thống bất ngờ khi xử lý đơn hàng
    MISMATCH,    // Thông tin không khớp: sai STK nhận, đơn đã hủy, hoặc không tìm thấy đơn
    IGNORED      // Giao dịch bị bỏ qua (tiền ra transferType=out, hoặc giao dịch không liên quan đơn hàng)
}

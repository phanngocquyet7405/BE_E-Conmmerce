package com.ecommerce.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 1xxx: Auth & Security
    UNAUTHENTICATED(1001, "Bạn chưa đăng nhập hoặc phiên làm việc không hợp lệ", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(1002, "Token đã hết hạn, vui lòng làm mới phiên đăng nhập", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(1003, "Chữ ký hoặc cấu trúc Token không hợp lệ", HttpStatus.UNAUTHORIZED),
    TOKEN_REVOKED(1004, "Token này đã bị thu hồi hoặc đã đăng xuất", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(1005, "Bạn không có quyền truy cập chức năng này", HttpStatus.FORBIDDEN),
    BAD_CREDENTIALS(1006, "Email hoặc mật khẩu không chính xác", HttpStatus.UNAUTHORIZED),
    EMAIL_ALREADY_EXISTS(1007, "Email này đã được đăng ký tài khoản", HttpStatus.CONFLICT),
    PASSWORD_NOT_MATCH(1008, "Mật khẩu hiện tại không chính xác", HttpStatus.BAD_REQUEST),
    ACCOUNT_NOT_ACTIVATED(1009, "Tài khoản chưa được kích hoạt, vui lòng kiểm tra email", HttpStatus.FORBIDDEN),
    USER_NOT_ACTIVATED(1009, "Tài khoản chưa được kích hoạt, vui lòng kiểm tra email", HttpStatus.FORBIDDEN),
    OTP_THROTTLED(1010, "Yêu cầu mã OTP quá nhanh hoặc vượt quá giới hạn. Vui lòng thử lại sau.", HttpStatus.TOO_MANY_REQUESTS),
    OTP_MAX_ATTEMPTS_EXCEEDED(1011, "Bạn đã nhập sai mã OTP quá 10 lần trong 1 giờ. Vui lòng thử lại sau 1 giờ.", HttpStatus.TOO_MANY_REQUESTS),
    AUTH_SERVICE_UNAVAILABLE(1012, "Dịch vụ xác thực tạm thời không khả dụng, vui lòng thử lại sau.", HttpStatus.SERVICE_UNAVAILABLE),
    RESET_TOKEN_INVALID(1013, "Mã xác thực đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.", HttpStatus.BAD_REQUEST),
    ACCOUNT_TEMP_LOCKED(1014, "Tài khoản tạm thời bị khóa do nhập sai mật khẩu nhiều lần. Vui lòng thử lại sau 15 phút.", HttpStatus.TOO_MANY_REQUESTS),

    // 2xxx: Validation & Request
    VALIDATION_ERROR(2001, "Dữ liệu gửi lên không thỏa mãn yêu cầu", HttpStatus.BAD_REQUEST),
    MALFORMED_JSON(2002, "Định dạng JSON gửi lên bị sai cú pháp", HttpStatus.BAD_REQUEST),
    INVALID_FILE_TYPE(2003, "Định dạng file không được hỗ trợ (chỉ chấp nhận JPEG, PNG, WEBP, GIF)", HttpStatus.BAD_REQUEST),
    FILE_SIZE_EXCEEDED(2004, "Kích thước file vượt quá giới hạn tối đa cho phép (10MB)", HttpStatus.BAD_REQUEST),
    FILE_UPLOAD_FAILED(2005, "Lỗi xảy ra trong quá trình lưu trữ file media", HttpStatus.INTERNAL_SERVER_ERROR),

    // 3xxx: Catalog, Product, Category, Brand & Cart
    PRODUCT_NOT_FOUND(3001, "Không tìm thấy sản phẩm yêu cầu", HttpStatus.NOT_FOUND),
    PRODUCT_SKU_EXISTS(3002, "Mã SKU sản phẩm đã tồn tại", HttpStatus.CONFLICT),
    CATEGORY_NOT_FOUND(3003, "Không tìm thấy danh mục sản phẩm", HttpStatus.NOT_FOUND),
    INSUFFICIENT_STOCK(3004, "Số lượng trong kho không đủ đáp ứng", HttpStatus.BAD_REQUEST),
    CART_EMPTY(3005, "Giỏ hàng hiện đang trống, không thể tiến hành đặt hàng", HttpStatus.BAD_REQUEST),
    CART_ITEM_NOT_FOUND(3006, "Không tìm thấy sản phẩm này trong giỏ hàng", HttpStatus.NOT_FOUND),
    BRAND_NOT_FOUND(3007, "Không tìm thấy thương hiệu yêu cầu", HttpStatus.NOT_FOUND),

    // 4xxx: Profile & Address
    ADDRESS_NOT_FOUND(4001, "Không tìm thấy địa chỉ giao hàng", HttpStatus.NOT_FOUND),
    ADDRESS_LIMIT_EXCEEDED(4002, "Số lượng địa chỉ nhận hàng đã đạt giới hạn tối đa", HttpStatus.BAD_REQUEST),
    METHOD_NOT_ALLOWED(4005, "Phương thức HTTP không được hỗ trợ cho đường dẫn này", HttpStatus.METHOD_NOT_ALLOWED),

    // 5xxx: Order & Checkout
    ORDER_NOT_FOUND(5001, "Không tìm thấy đơn hàng yêu cầu", HttpStatus.NOT_FOUND),
    ORDER_CANNOT_BE_CANCELLED(5002, "Đơn hàng đã được xử lý/vận chuyển, không thể hủy", HttpStatus.BAD_REQUEST),
    INVALID_ORDER_STATUS_TRANSITION(5003, "Chuyển trạng thái đơn hàng không hợp lệ", HttpStatus.BAD_REQUEST),
    PAYMENT_METHOD_NOT_SUPPORTED(5004, "Phương thức thanh toán không được hỗ trợ hoặc chưa tích hợp", HttpStatus.BAD_REQUEST),
    PAYMENT_REQUIRED_BEFORE_SHIPPING(5005, "Đơn hàng chưa thanh toán (PAID), không thể chuyển sang giao hàng", HttpStatus.BAD_REQUEST),
    ORDER_ALREADY_REFUNDED(5006, "Đơn hàng đã được hoàn tiền trước đó", HttpStatus.BAD_REQUEST),

    // 9xxx: System
    INTERNAL_SERVER_ERROR(9999, "Lỗi hệ thống máy chủ, vui lòng thử lại sau", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
}

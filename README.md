# E-Commerce Backend (Spring Boot 3 + MongoDB Atlas + Redis)

Dự án Backend Thương mại điện tử (Mô hình Cửa hàng độc lập / Single-Seller) tuân thủ kiến trúc 3 lớp MVC chuẩn mực:
- **Ngôn ngữ**: Java 21 LTS
- **Framework**: Spring Boot 3.3.4
- **Cơ sở dữ liệu**: MongoDB Atlas (Spring Data MongoDB)
- **Bộ nhớ đệm & Quản lý phiên**: Redis Upstash / Local (Whitelist Refresh Token 7 ngày, Blacklist Token, Rate Limiting, OTP Cache, User Block synchronization)
- **Cổng thanh toán**: Tích hợp VietQR tự động qua SePay (Idempotent Webhook, Chống race condition, Xử lý chuyển thiếu/thừa tiền D5/D6, Máy trạng thái nghiêm ngặt D4/D7)
- **Bảo mật**: Spring Security 6 + JJWT 0.12.6 (Fail-closed, Rate Limiting theo IP, Chống IDOR, Zero Hardcoded Secrets)

---

## 📚 TÀI LIỆU DỰ ÁN CHI TIẾT

- **[Tài liệu Bàn giao API cho Frontend (Android & Next.js)](docs/FRONTEND_HANDOVER.md)**: Đặc tả toàn bộ API, luồng Auth/OTP, Đặt hàng & VietQR SePay, Máy trạng thái đơn hàng, Quản trị Admin, Thống kê doanh thu D9 và bảng mã lỗi ErrorCode.
- **[Hướng dẫn Triển khai & Vận hành (Deployment & Ops Guide)](docs/DEPLOYMENT_GUIDE.md)**: Cấu hình tối ưu Render Free Tier (512MB RAM, JVM flags), thiết lập MongoDB Replica Set, Redis Upstash, cấu hình Webhook SePay và danh mục biến môi trường.
- **[Báo cáo Audit Hệ thống](AUDIT_REPORT.md)**: Chi tiết báo cáo rà soát mã nguồn toàn diện.
- **[Kiến trúc Hệ thống](ARCHITECTURE.md)**: Thiết kế sơ đồ lớp và kiến trúc chi tiết.

---

## 🚀 HƯỚNG DẪN KHỞI CHẠY NHANH

### 1. Cấu hình biến môi trường
Tạo file `.env` tại thư mục gốc từ `.env.example`:
```env
MONGODB_URI=mongodb://localhost:27017/ecommerce_db
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_SECRET=dGhpcy1pcy1hLXNlY3VyZS1iYXNlNjQta2V5LTMyLWJ5dGVzLXBsZWFzZS1jaGFuZ2U=
SEPAY_BANK_ID=MBBank
SEPAY_BANK_ACCOUNT=0123456789
SEPAY_ACCOUNT_HOLDER=NGUYEN VAN A
SEPAY_API_KEY=test-api-key
```

### 2. Biên dịch và kiểm thử
```bash
# Kiểm tra biên dịch
mvn -q -DskipTests compile

# Chạy toàn bộ 113 unit & integration tests
mvn clean test
```

### 3. Chạy ứng dụng
```bash
mvn spring-boot:run
```

- **Health check**: `http://localhost:8080/health`
- **Swagger UI (Môi trường Dev)**: `http://localhost:8080/swagger-ui/index.html`

---
name: ecommerce-backend
description: >-
  Chuyên gia phát triển, bảo trì và kiểm thử hệ thống E-Commerce Backend (Java 21, Spring Boot 3.3.4, MongoDB Atlas, Redis Upstash, SePay VietQR, OTP Email, Render 512MB). Kích hoạt khi cần bảo trì, mở rộng tính năng, debug lỗi, hoặc tích hợp API với Frontend/Mobile.
---

# E-COMMERCE BACKEND EXPERT RUNBOOK & GUIDE

Tài liệu hướng dẫn toàn diện dành cho Agent AI khi làm việc với codebase **E-Commerce Backend**.

---

## 1. CẤU TRÚC THƯ MỤC DỰ ÁN (PACKAGE STRUCTURE)

Root package: `com.ecommerce`

```text
src/main/java/com/ecommerce/
├── common/             # Constants, ApiResponse wrapper, Error Codes
│   ├── constant/       # ApiRoutes (quy hoạch tập trung mọi URL endpoint)
│   └── response/       # ApiResponse<T> chuẩn RESTful
├── config/             # Cấu hình Spring (MongoConfig, RedisConfig, SecurityConfig, WebMvcConfig, AppProperties)
├── controller/         # REST Controllers (Auth, Product, Order, Payment, SePayWebhook, Profile, Media, Cart)
├── document/           # MongoDB Documents (User, Product, Category, Brand, Order, Cart, SePayTransaction)
├── dto/                # Data Transfer Objects (request, response)
├── exception/          # GlobalExceptionHandler, AppException, ErrorCode
├── mapper/             # MapStruct / Manual Mappers
├── repository/         # Spring Data Mongo Repositories
├── security/           # RateLimitFilter, JwtAuthenticationFilter, CustomUserDetails, UserBlockService
├── service/            # Interfaces nghiệp vụ
│   └── impl/           # Implementations (AuthServiceImpl, OrderServiceImpl, PaymentServiceImpl, SePayService, ...)
└── util/               # JwtUtil (sinh và xác thực Token), PasswordEncoder
```

---

## 2. QUY TRÌNH KIỂM TRA & BIÊN DỊCH BẮT BUỘC (MANDATORY CHECKS)

Trước khi đề xuất hoặc hoàn thành bất kỳ thay đổi mã nguồn nào, Agent BẮT BUỘC chạy các lệnh sau:

1. **Biên dịch không chạy test:**
   ```powershell
   mvn -q -DskipTests compile
   ```
2. **Chạy toàn bộ 32 Unit Tests:**
   ```powershell
   mvn test
   ```
   *Yêu cầu:* Toàn bộ test suites phải **BUILD SUCCESS** (0 failures, 0 errors).
   - `SePayWebhookControllerTest` (10 tests)
   - `UserBlockMechanismTest` (7 tests)
   - `AuthEmailOtpFlowTest` (5 tests)
   - `JwtUtilTest` (5 tests)
   - `OrderServiceTest` (4 tests)
   - `EcommerceApplicationTests` (1 test)

---

## 3. DANH SÁCH BIẾN MÔI TRƯỜNG CỐT LÕI (ENVIRONMENT MATRIX)

Mọi bí mật đều đọc từ file `.env` ở local hoặc cấu hình trên **Render Dashboard** (với `sync: false`):

| Biến môi trường | Ý nghĩa | Ví dụ giá trị |
| :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | Profile chạy ứng dụng | `dev` (local) hoặc `prod` (Render) |
| `PORT` | Cổng ứng dụng lắng nghe | `8080` |
| `MONGODB_URI` | Chuỗi kết nối MongoDB | `mongodb://localhost:27017/ecommerce_dev_db` hoặc Atlas URI |
| `REDIS_URL` | Chuỗi kết nối Redis Upstash | `rediss://default:...@...:6379` |
| `REDIS_SSL_ENABLED` | Bật SSL cho Redis | `true` (Upstash) hoặc `false` (Local) |
| `JWT_ACCESS_SECRET` | Khóa ký Access Token (Base64 >= 256 bits) | Sinh bằng `openssl rand -base64 32` |
| `JWT_REFRESH_SECRET` | Khóa ký Refresh Token (Base64 >= 256 bits) | Sinh bằng `openssl rand -base64 32` |
| `SPRING_MAIL_HOST` | Máy chủ SMTP gửi OTP | `in-v3.mailjet.com` (hoặc `smtp.gmail.com`) |
| `SPRING_MAIL_PORT` | Cổng kết nối SMTP | `80` (Mailjet tránh bị Render chặn) hoặc `587` |
| `SPRING_MAIL_USERNAME` | Tài khoản gửi mail / Mailjet API Key | API Key lấy từ Mailjet |
| `SPRING_MAIL_PASSWORD` | Mật khẩu ứng dụng / Mailjet Secret Key | Secret Key lấy từ Mailjet |
| `SPRING_MAIL_FROM` | Email hiển thị người gửi | Email đã verify trên Mailjet (vd `furiousfast189@gmail.com`) |
| `SEPAY_BANK_ID` | Mã định danh ngân hàng SePay VietQR | `MBBank`, `VCB`, `ACB`, `TPBank` |
| `SEPAY_BANK_ACCOUNT` | Số tài khoản ngân hàng nhận tiền | `0862483762` |
| `SEPAY_ACCOUNT_HOLDER` | Tên chủ tài khoản thụ hưởng | `NGUYEN VAN A` |
| `SEPAY_API_KEY` | Khóa API SePay (nếu dùng xác thực API Key) | API Key lấy từ SePay |
| `SEPAY_WEBHOOK_SECRET` | Khóa bí mật HMAC-SHA256 Webhook | Lấy từ trang Webhooks trên `my.sepay.vn` |
| `CLOUDINARY_*` | Cấu hình lưu trữ ảnh Cloudinary | `cloud-name`, `api-key`, `api-secret` |
| `CORS_ALLOWED_ORIGINS` | Danh sách domain frontend được phép | `http://localhost:3000,http://localhost:5173,http://localhost:8081` |

---

## 4. QUY TẮC PHÁT TRIỂN & CHỐNG HỒNG HỆ THỐNG

### 4.1. Không bao giờ phá vỡ luồng SePay Webhook
- Endpoint Webhook: `POST /api/payments/sepay-webhook`.
- Header xác thực: `X-SePay-Signature`, `X-SePay-Timestamp`.
- Luôn kiểm tra chữ ký HMAC-SHA256 constant-time bằng `MessageDigest.isEqual`.
- Giữ vững tính Idempotency: `sePayTransactionRepository.existsBySepayId(id)`.

### 4.2. Không bao giờ làm hỏng luồng Đăng ký & OTP
- User đăng ký mặc định `isActive = false`, sinh OTP 6 số (5 phút).
- `EmailServiceImpl` gửi qua Mailjet cổng 80 (hoặc SMTP). Bắt buộc luôn bọc try-catch để log fallback OTP, không làm văng lỗi 500 khi môi trường mạng có sự cố.
- Login phải kiểm tra `user.isActive()`. Nếu `false` ném `ErrorCode.USER_NOT_ACTIVATED (1007)`.

### 4.3. Concurrency & Transaction
- Thao tác trừ tồn kho trong Checkout phải luôn dùng `mongoTemplate.findAndModify` với điều kiện `stock_quantity >= quantity` và `$inc: -quantity`.
- Gắn `@Transactional(rollbackFor = Exception.class)`.
- Khi có lỗi sau khi trừ tồn kho, phải có compensating action cộng lại số lượng đã trừ vào tồn kho MongoDB.

---

## 5. HƯỚNG DẪN XỬ LÝ LỖI PHỔ BIẾN (TROUBLESHOOTING RUNBOOK)

1. **Lỗi `SocketTimeoutException: Connect timed out` khi gửi mail trên Render:**
   - *Nguyên nhân:* Render Free chặn cổng 587 và 465.
   - *Khắc phục:* Chuyển sang dùng Mailjet SMTP qua **cổng 80** (`SPRING_MAIL_HOST=in-v3.mailjet.com`, `SPRING_MAIL_PORT=80`).
2. **Lỗi `Failed looking up SRV record for mongodb.net`:**
   - *Nguyên nhân:* Chuỗi URI Atlas gõ nhầm ký tự (như `yl7flny` thay vì `y17f1ny`) hoặc mất kết nối DNS.
   - *Khắc phục:* Kiểm tra chính xác URI trên MongoDB Atlas Dashboard hoặc dùng URI local khi dev.
3. **Lỗi OOMKilled (Exit Code 137) trên Render 512MB:**
   - *Khắc phục:* Giữ nguyên JVM flags trong Dockerfile: `-XX:MaxRAMPercentage=60.0 -XX:MaxMetaspaceSize=110m -Xss512k -XX:+UseSerialGC -XX:+TieredCompilation -XX:TieredStopAtLevel=1`.

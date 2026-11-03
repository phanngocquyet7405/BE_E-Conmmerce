# PROJECT RULES: E-COMMERCE BACKEND

## 1. TỔNG QUAN HỆ THỐNG & CÔNG NGHỆ CỐT LÕI
- **Mô hình:** Single-Seller / Cửa hàng độc lập (3-Tier Architecture: Controller -> Service -> Repository).
- **Ngôn ngữ & Framework:** Java 21 LTS, Spring Boot 3.3.4, Spring Security 6, Spring Data MongoDB, Spring Data Redis (Lettuce).
- **Cơ sở dữ liệu:**
  - **MongoDB Atlas:** Lưu trữ Document chính (Users, Products, Categories, Brands, Orders, Carts, SePayTransactions).
  - **Redis (Upstash / Local):** Token Whitelist (Refresh Token TTL 7 ngày), Token Blacklist (Access Token đã logout), Rate Limiting, OTP Cache (TTL 5 phút), Data Cache (@Cacheable TTL 24h cho Categories & Brands).
- **Môi trường triển khai:** Render Free Tier (512MB RAM container, CPU shared, Cold start sau 15 phút idle).
  - JVM Flags tối ưu: `-XX:MaxRAMPercentage=60.0 -XX:MaxMetaspaceSize=110m -Xss512k -XX:+UseSerialGC -XX:+TieredCompilation -XX:TieredStopAtLevel=1`.
  - Tắt `auto-index-creation` trên môi trường Production (`application-prod.yml`).

---

## 2. QUY TẮC BẢO MẬT BẮT BUỘC (SECURITY RULES)
1. **Zero Hardcoded Secrets:**
   - Tuyệt đối KHÔNG hard-code hay in mật khẩu, API key, JWT secret, Gmail app password vào code, log, test hoặc file tracked bởi git.
   - Mọi bí mật bắt buộc đọc từ biến môi trường qua `${TEN_BIEN}`.
   - File `.env` chứa bí mật thật phải luôn nằm trong `.gitignore` và `.dockerignore`. File `.env.example` chỉ chứa placeholder `change-me`.
2. **Nguyên tắc Fail-Closed (An toàn khi có sự cố):**
   - Khi Redis mất kết nối/lỗi: `RedisTokenServiceImpl.isRefreshTokenValid` bắt buộc `return false;`.
   - SePay Webhook: Thiếu header hoặc secret rỗng bắt buộc trả `HTTP 401 Unauthorized`.
3. **Bảo mật JWT:**
   - Khóa ký JWT phải là chuỗi Base64 >= 256 bits (32 bytes). Khởi động ứng dụng phải fail-fast nếu thiếu hoặc khóa yếu.
   - `JwtAuthenticationFilter` trích xuất `userId`, `email`, `roles` trực tiếp từ JWT Claims đã verify chữ ký, KHÔNG query MongoDB trên mỗi request.
   - Kiểm tra trạng thái người dùng bị khóa dựa trên `iat` của token so với thời điểm block trong Redis.
4. **Rate Limiting:**
   - Áp dụng `RateLimitFilter` dựa trên Redis IP: tối đa 5 req/phút cho `/auth/login`, `/auth/register`; 10 req/phút cho refresh token và upload ảnh.
5. **CORS & Upload An toàn:**
   - CORS chỉ định domain cụ thể (`CORS_ALLOWED_ORIGINS`), không dùng `*` chung với `allowCredentials(true)`.
   - `MediaServiceImpl`: Kiểm tra Magic Bytes nhị phân xác thực file ảnh, tự sinh đuôi an toàn (`.jpg`, `.png`, `.webp`), stream trực tiếp lên Cloudinary.

---

## 3. QUY TẮC DỮ LIỆU & CONCURRENCY (DATA & TRANSACTION)
1. **Chống bán vượt tồn kho (Overselling Race Condition):**
   - Thao tác trừ tồn kho khi Checkout BẮT BUỘC dùng truy vấn nguyên tử `MongoTemplate.findAndModify`:
     `Query: { _id: productId, stock_quantity: { $gte: quantity }, active: true }`
     `Update: { $inc: { stock_quantity: -quantity } }`
   - Bọc `@Transactional(rollbackFor = Exception.class)` và luôn có cơ chế hoàn tồn kho bù (compensating rollback) nếu phát sinh lỗi sau khi đã trừ kho.
2. **Chỉ mục kết hợp (Compound Index):**
   - Mọi Entity truy vấn kết hợp lọc + sắp xếp phải có `@CompoundIndex`:
     - `Product`: `{ active: 1, created_at: -1 }`
     - `Order`: `{ user_id: 1, created_at: -1 }`, `{ status: 1, created_at: -1 }`
3. **Phân trang an toàn (Pagination Hard Cap):**
   - Giới hạn trần tham số `size` trong khoảng `[1, 50]` ở mọi Controller/Service để chống DoS tràn RAM heap.
   - Tìm kiếm từ khóa text phải dùng `Pattern.quote(keyword)`.

---

## 4. QUY TẮC LUỒNG NGHIỆP VỤ (BUSINESS LOGIC)

### 4.1. Luồng Xác thực & OTP (Auth & OTP Flow)
1. **Đăng ký (`/api/v1/auth/register`):**
   - Lưu User vào DB với `isActive = false`, sinh OTP 6 số (thời hạn 5 phút).
   - Gửi OTP qua `EmailService`. Luôn bọc try-catch để log fallback OTP lên console/logs nếu môi trường Cloud (như Render Free) chặn cổng SMTP 587/465, không làm crash API đăng ký.
2. **Chặn Đăng nhập:**
   - Trong `/api/v1/auth/login`, nếu `isActive == false` bắt buộc chặn lại, ném `AppException(ErrorCode.USER_NOT_ACTIVATED)` thông báo yêu cầu xác thực email.
3. **Xác thực OTP (`/api/v1/auth/verify-otp`):**
   - Nhận `email` và `otp`. Khớp mã và chưa hết hạn thì cập nhật `isActive = true`, `emailVerified = true`. Lúc này tài khoản mới được đăng nhập.

### 4.2. Luồng Thanh toán SePay VietQR (`/api/payments`)
1. **Mã đơn hàng:** Sinh dạng `BETES` + 8 chữ số (ví dụ `BETES12345678`).
2. **VietQR Link:** Trả về định dạng:
   `https://qr.sepay.vn/img?acc={bank-id}-{bank-account}&template=compact&amount={amount}&des={orderCode}`
3. **Webhook SePay (`/api/payments/sepay-webhook`):**
   - Xác thực HMAC-SHA256: `sha256=` + hex(HMAC-SHA256(secret, timestamp + "." + rawBody)).
   - So sánh constant-time bằng `MessageDigest.isEqual`.
   - Chống Replay: Kiểm tra `|now - timestamp| <= 300` giây.
   - Idempotency: Kiểm tra `existsBySepayId(id)` và bắt `DuplicateKeyException` khi lưu `SePayTransaction`.
   - Trừ nợ nguyên tử: Điều kiện `paymentStatus == UNPAID` và số tiền chuyển >= `finalAmount`.

---

## 5. BỘ LỆNH THƯỜNG DÙNG
- **Biên dịch kiểm tra:** `mvn -q -DskipTests compile`
- **Chạy toàn bộ Unit Tests:** `mvn test` (Toàn bộ 32 unit tests bắt buộc phải PASS 100%).
- **Chạy local:** Cấu hình file `.env` tại thư mục gốc và chạy `mvn spring-boot:run`.

# HƯỚNG DẪN TRIỂN KHAI VÀ VẬN HÀNH HỆ THỐNG (DEPLOYMENT & OPS GUIDE)

> **Dự án**: BE_E-Commerce (Spring Boot 3.3.4, Java 21 LTS, MongoDB Atlas, Redis Upstash, Cloudinary, SePay VietQR)  
> **Môi trường mục tiêu**: Render Free Tier (512MB RAM, CPU Shared, Cold Start sau 15 phút idle)

---

## 1. TỐI ƯU HÓA TÀI NGUYÊN CHO RENDER FREE TIER (512MB RAM)

Render Free Tier có giới hạn phần cứng nghiêm ngặt: **512MB RAM**. Nếu JVM sử dụng vượt quá ngưỡng này, tiến trình sẽ bị kernel tiêu diệt ngay lập tức (`OOMKilled - Exit code 137`).

### 1.1. Cấu hình JVM Flags bắt buộc
Thiết lập biến môi trường `JAVA_TOOL_OPTIONS` trên Render Dashboard:

```bash
JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:MaxMetaspaceSize=110m -Xss512k -XX:+UseSerialGC -XX:+TieredCompilation -XX:TieredStopAtLevel=1"
```

**Giải thích các cờ tối ưu:**
- `-XX:MaxRAMPercentage=60.0`: Giới hạn heap tối đa ở mức 60% của 512MB (~307MB RAM), để dành ~205MB cho Metaspace, Thread Stacks, Native Memory và hệ điều hành.
- `-XX:MaxMetaspaceSize=110m`: Chặn rò rỉ bộ nhớ Metaspace class metadata.
- `-Xss512k`: Giảm kích thước ngăn xếp mỗi luồng từ 1MB xuống 512KB để tiết kiệm RAM.
- `-XX:+UseSerialGC`: Garbage Collector đơn luồng tối ưu tiêu thụ RAM thấp nhất (phù hợp container 1 vCPU).
- `-XX:+TieredCompilation -XX:TieredStopAtLevel=1`: Tắt C2 compiler (chỉ dùng C1 compiler) giúp giảm mức tiêu thụ RAM và đẩy nhanh thời gian khởi động (giảm cold start delay).

### 1.2. Cấu hình tắt Auto Index Creation trên Production
Trong file `application-prod.yml`, thuộc tính:
```yaml
spring:
  data:
    mongodb:
      auto-index-creation: false
```
đã được thiết lập nhằm tránh việc ứng dụng quét và tạo index làm nghẽn CPU/RAM lúc khởi động. Các chỉ mục cần được khởi tạo trước trên MongoDB Atlas (hoặc môi trường dev).

---

## 2. DANH MỤC BIẾN MÔI TRƯỜNG BẮT BUỘC (ENVIRONMENT VARIABLES)

Hệ thống tuân thủ nguyên tắc **Zero Hardcoded Secrets**: Toàn bộ thông tin nhạy cảm bắt buộc cấu hình qua biến môi trường. Thiếu hoặc sai định dạng sẽ khiến ứng dụng kích hoạt cơ chế **Fail-Fast** khi khởi động.

| Tên biến môi trường | Bắt buộc | Ví dụ giá trị | Mục đích & Lưu ý |
| :--- | :---: | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | **Có** | `prod` | Chuyển chế độ sang Production (tắt Swagger UI, bật bảo mật nâng cao). |
| `SERVER_PORT` | **Có** | `8080` (hoặc `${PORT}`) | Render tự động cấp phát cổng qua biến `$PORT`. |
| `MONGODB_URI` | **Có** | `mongodb+srv://user:pass@cluster0.xxx.mongodb.net/ecommerce_db?retryWrites=true&w=majority` | Chuỗi kết nối MongoDB Atlas (khuyến nghị Replica Set để hỗ trợ multi-document transactions). |
| `REDIS_URL` | **Có** | `rediss://default:token@xxx.upstash.io:6379` | Chuỗi kết nối Redis Upstash (kèm SSL `rediss://`). |
| `JWT_SECRET` | **Có** | `dGhpcy1pcy1hLXNlY3VyZS1iYXNlNjQta2V5LTMyLWJ5dGVzLXBsZWFzZS1jaGFuZ2U=` | Khóa ký HMAC-SHA256 dạng **Base64 >= 256 bits (32 bytes)**. Nếu yếu sẽ bị từ chối lúc khởi động. |
| `JWT_ACCESS_EXPIRATION_MS` | Không | `86400000` (1 ngày) | Thời gian sống của Access Token. |
| `JWT_REFRESH_EXPIRATION_MS`| Không | `604800000` (7 ngày) | Thời gian sống của Refresh Token (lưu Redis Whitelist). |
| `SEPAY_BANK_ID` | **Có** | `MBBank` | Mã ngân hàng nhận tiền thụ hưởng (VD: `MBBank`, `Vietcombank`, `Techcombank`). Không dùng default giả! |
| `SEPAY_BANK_ACCOUNT` | **Có** | `0123456789` | Số tài khoản ngân hàng thụ hưởng nhận thanh toán VietQR. |
| `SEPAY_ACCOUNT_HOLDER` | **Có** | `NGUYEN VAN A` | Tên chủ tài khoản ngân hàng thụ hưởng (viết hoa không dấu). |
| `SEPAY_API_KEY` | **Có** | `SEPAY_APIKEY_SECRET_STRING` | API Token được cấp từ SePay Dashboard (dùng để xác thực webhook `Authorization: Apikey <key>`). |
| `SEPAY_ORDER_CODE_PREFIX` | Không | `BETES` | Tiền tố mã đơn hàng sinh ra (VD: `BETES12345678`). |
| `CLOUDINARY_CLOUD_NAME` | **Có** | `your_cloud_name` | Tên Cloudinary Cloud lưu trữ ảnh. |
| `CLOUDINARY_API_KEY` | **Có** | `123456789012345` | API Key Cloudinary. |
| `CLOUDINARY_API_SECRET` | **Có** | `abcdefghijklmnopqrstuvwxyz` | API Secret Cloudinary. |
| `MAIL_HOST` | Không | `smtp.gmail.com` | Máy chủ gửi thư SMTP. |
| `MAIL_PORT` | Không | `587` | Cổng SMTP (587 STARTTLS). |
| `MAIL_USERNAME` | Không | `your_email@gmail.com` | Email gửi OTP. |
| `MAIL_PASSWORD` | Không | `your_16_char_app_password` | App Password của Gmail. |
| `CORS_ALLOWED_ORIGINS` | **Có** | `https://your-frontend.vercel.app,https://admin.your-domain.com` | Danh sách domain Frontend được phép gọi API (phân tách bởi dấu phẩy, không dùng `*` chung với credentials). |

---

## 3. CẤU HÌNH WEBHOOK SEPAY VIETQR

Để hệ thống tự động xác nhận thanh toán khi khách hàng quét mã VietQR:

1. Đăng nhập vào [SePay Dashboard](https://my.sepay.vn).
2. Vào mục **Cấu hình Webhook** -> **Thêm Webhook**.
3. Điền các thông số:
   - **URL Webhook**: `https://your-backend.onrender.com/api/payments/sepay-webhook`
   - **Phương thức gửi**: `POST`
   - **Kiểu dữ liệu**: `JSON`
   - **Xác thực**: Điền API Key của bạn (khớp với giá trị biến môi trường `SEPAY_API_KEY`).
4. **Cơ chế bảo mật Webhook**:
   - Webhook hỗ trợ cả xác thực chuẩn của SePay: Header `Authorization: Apikey <SEPAY_API_KEY>` và chữ ký HMAC-SHA256 (`X-SePay-Signature`, `X-SePay-Timestamp`).
   - Tự động chống replay attack (sai lệch thời gian quá 300 giây sẽ bị từ chối).
   - Tự động chống trùng lặp (Idempotency) dựa trên chỉ mục duy nhất `sepay_id`.

---

## 4. CHỈ MỤC CƠ SỞ DỮ LIỆU CẦN THIẾT TRÊN MONGODB ATLAS

Trên MongoDB Atlas, đảm bảo các Collection có các chỉ mục (Indexes) sau:

```javascript
// 1. Collection 'users'
db.users.createIndex({ "email": 1 }, { unique: true });

// 2. Collection 'products'
db.products.createIndex({ "sku": 1 }, { unique: true });
db.products.createIndex({ "slug": 1 }, { unique: true });
db.products.createIndex({ "category_id": 1 });
db.products.createIndex({ "brand_id": 1 });
db.products.createIndex({ "active": 1, "created_at": -1 }); // Compound Index cho phân trang/lọc sản phẩm

// 3. Collection 'orders'
db.orders.createIndex({ "order_code": 1 }, { unique: true });
db.orders.createIndex({ "user_id": 1, "created_at": -1 });
db.orders.createIndex({ "status": 1, "created_at": -1 });
db.orders.createIndex({ "status": 1, "payment_status": 1, "created_at": -1 }); // Index hỗ trợ cron auto-cancel

// 4. Collection 'sepay_transactions'
db.sepay_transactions.createIndex({ "sepay_id": 1 }, { unique: true });
db.sepay_transactions.createIndex({ "order_code": 1, "created_at": -1 });
db.sepay_transactions.createIndex({ "status": 1, "created_at": -1 });
```

---

## 5. DOCKERFILE TRIỂN KHAI NHẸ CHO RENDER

```dockerfile
# Stage 1: Build source code
FROM maven:3.9.8-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Lightweight JRE runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/target/ecommerce-backend-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_TOOL_OPTIONS -jar app.jar"]
```

---

## 6. KIỂM TRA SỨC KHỎE & VẬN HÀNH (MONITORING & HEALTH CHECK)

- **Health Check Endpoint**: `GET /health`
  - Render dùng endpoint này để kiểm tra container sẵn sàng tiếp nhận traffic.
  - Phản hồi: `HTTP 200 OK` kèm `{"status": "UP"}`.
- **Tự động thức tỉnh (Keep-Alive)**:
  - Do Render Free Tier sẽ vào trạng thái ngủ sau 15 phút idle, có thể dùng dịch vụ như UptimeRobot hoặc Cron-job ngoài ping vào `/health` mỗi 10 phút nếu muốn giảm thiểu cold start.
- **Lệnh build & test**:
  ```bash
  # Biên dịch kiểm tra cú pháp:
  mvn -q -DskipTests compile

  # Chạy toàn bộ test kiểm thử:
  mvn clean test
  ```

# TÀI LIỆU BÀN GIAO TÍCH HỢP FRONTEND (NEXT.JS & ANDROID) VÀ DEPLOYMENT

> **Dự án:** BE_E-Commerce (Spring Boot 3.3.4, Java 21, MongoDB Atlas, Redis Upstash, SePay VietQR)  
> **Branch:** `audit-fixes`  
> **Thời điểm cập nhật:** 06/11/2026  

---

## 1. TỔNG QUAN CÁC THAY ĐỔI CỐT LÕI (CHANGELOG CHO FRONTEND)

### 1.1. Luồng Xác thực Email & Đăng nhập (Auth & OTP Flow)
1. **Đăng ký (`POST /api/v1/auth/register`):**
   - Tài khoản đăng ký mới sẽ có trạng thái `isActive = false`, `emailVerified = false`.
   - Hệ thống tự sinh mã OTP 6 chữ số có hiệu lực trong **5 phút** và gửi qua email.
2. **Chặn Đăng nhập:**
   - Khi gọi `POST /api/v1/auth/login`, nếu tài khoản chưa kích hoạt email:
     - **HTTP Status:** `403 Forbidden`
     - **Error Code:** `1009` (`ACCOUNT_NOT_ACTIVATED` / `USER_NOT_ACTIVATED`)
     - **Message:** `"Tài khoản chưa được kích hoạt, vui lòng kiểm tra email"`
     - **Hành động Frontend (Next.js & Android):** Bắt mã lỗi `1009` và tự động chuyển hướng người dùng sang màn hình nhập mã OTP xác thực email (`/verify-otp`).
3. **Xác thực OTP (`POST /api/v1/auth/verify-otp`):**
   - Nhận `email` và `otp`. Khi nhập đúng mã còn hiệu lực, tài khoản chuyển sang `isActive = true` và người dùng có thể đăng nhập bình thường.

### 1.2. Luồng Đặt hàng & Thanh toán SePay VietQR
1. **Mã đơn hàng chuẩn hóa:** Sinh dạng `${SEPAY_ORDER_CODE_PREFIX}` + 8 chữ số ngẫu nhiên (mặc định: `BETES12345678`).
2. **VietQR Link trả về ngay trong kết quả Checkout:**
   - Khi chọn `paymentMethod = "SEPAY"` hoặc `"BANKING"`, response của API Checkout và API Chi tiết đơn hàng trả về sẵn:
     - `qrImageUrl`: Link ảnh VietQR tự động sinh dạng:  
       `https://qr.sepay.vn/img?acc={bankId}-{bankAccount}&template=compact&amount={finalAmount}&des={orderCode}`
     - `bankName`: Mã định danh ngân hàng nhận tiền.
     - `accountNumber`: Số tài khoản nhận tiền.
     - `accountHolder`: Tên chủ tài khoản thụ hưởng.
     - `transferContent`: Nội dung chuyển khoản bắt buộc trùng `orderCode`.
3. **Tự động hủy đơn hàng chưa thanh toán:**
   - Các đơn thanh toán chuyển khoản (`SEPAY`, `BANKING`) nếu ở trạng thái `PENDING` và `paymentStatus = "UNPAID"` quá **15 phút** sẽ được background job tự động chuyển sang `CANCELLED` và hoàn trả số lượng tồn kho cho sản phẩm.
   - Frontend nên hiển thị bộ đếm ngược (countdown 15:00) trên màn hình thanh toán.

### 1.3. Quy tắc CORS nghiêm ngặt
- Hệ thống **không chấp nhận wildcard `*`** khi client gửi kèm cookie hoặc credentials.
- Mọi domain frontend chạy ở local hoặc production bắt buộc phải được khai báo trong biến `CORS_ALLOWED_ORIGINS` (ví dụ: `http://localhost:3000,http://localhost:5173,https://my-shop.vercel.app`).

---

## 2. CHI TIẾT ENDPOINTS CHO FRONTEND

### 2.1. Đăng ký tài khoản
- **Endpoint:** `POST /api/v1/auth/register`
- **Quyền:** Public
- **Request Body:**
```json
{
  "fullName": "Nguyễn Văn A",
  "email": "nguyenvana@gmail.com",
  "password": "Password123@",
  "phone": "0987654321"
}
```
- **Response `200 OK`:**
```json
{
  "code": 1000,
  "message": "Đăng ký tài khoản thành công. Vui lòng kiểm tra email để nhận mã OTP kích hoạt tài khoản.",
  "result": null
}
```

---

### 2.2. Xác thực mã OTP
- **Endpoint:** `POST /api/v1/auth/verify-otp`
- **Quyền:** Public

> [!WARNING]
> Endpoint này **CHỈ DÙNG ĐỂ KÍCH HOẠT TÀI KHOẢN ĐĂNG KÝ MỚI**.
> **Tuyệt đối KHÔNG DÙNG endpoint này cho luồng Quên mật khẩu!** Luồng Quên mật khẩu bắt buộc phải dùng `POST /api/v1/auth/verify-reset-otp` để nhận `resetToken`.
- **Request Body:**
```json
{
  "email": "nguyenvana@gmail.com",
  "otp": "123456"
}
```
- **Response `200 OK`:**
```json
{
  "code": 1000,
  "message": "Kích hoạt tài khoản thành công! Bây giờ bạn có thể đăng nhập.",
  "result": true
}
```
- **Response lỗi (`400 Bad Request`):**
```json
{
  "code": 2001,
  "error": "VALIDATION_ERROR",
  "message": "Mã OTP không chính xác hoặc đã hết hạn"
}
```

---

### 2.3. Đăng nhập
- **Endpoint:** `POST /api/v1/auth/login`
- **Quyền:** Public
- **Request Body:**
```json
{
  "email": "nguyenvana@gmail.com",
  "password": "Password123@"
}
```
- **Response thành công `200 OK`:**
```json
{
  "code": 1000,
  "result": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 3600
  }
}
```
- **Response khi tài khoản chưa xác thực email (`403 Forbidden`):**
```json
{
  "code": 1009,
  "error": "ACCOUNT_NOT_ACTIVATED",
  "message": "Tài khoản chưa được kích hoạt, vui lòng kiểm tra email"
}
```
> **Lưu ý Frontend:** Khi nhận `code: 1009`, chuyển ngay sang màn hình xác thực OTP kèm theo giá trị `email`.

---

### 2.4. Đặt hàng (Checkout)
- **Endpoint:** `POST /api/v1/orders/checkout`
- **Header:** `Authorization: Bearer <accessToken>`
- **Request Body:**
```json
{
  "addressId": "6543210abcdef1234567890a",
  "paymentMethod": "SEPAY",
  "note": "Giao giờ hành chính"
}
```
- **Response thành công `200 OK`:**
```json
{
  "code": 1000,
  "result": {
    "id": "672ae4b831fa2a001a111222",
    "orderCode": "BETES83920194",
    "userId": "672ae30031fa2a001a111111",
    "customerName": "Nguyễn Văn A",
    "customerEmail": "nguyenvana@gmail.com",
    "customerPhone": "0987654321",
    "shippingAddress": {
      "recipientName": "Nguyễn Văn A",
      "phone": "0987654321",
      "province": "Hà Nội",
      "district": "Cầu Giấy",
      "ward": "Dịch Vọng",
      "streetAddress": "Số 1 Cầu Giấy"
    },
    "items": [
      {
        "productId": "672000000000000000000001",
        "productName": "Áo Thun Nam Cotton",
        "sku": "ATN-COTTON-01",
        "price": 150000,
        "quantity": 2,
        "subTotal": 300000
      }
    ],
    "totalAmount": 300000,
    "shippingFee": 30000,
    "finalAmount": 330000,
    "paymentMethod": "SEPAY",
    "paymentStatus": "UNPAID",
    "status": "PENDING",
    "qrImageUrl": "https://qr.sepay.vn/img?acc=MBBank-0123456789&template=compact&amount=330000&des=BETES83920194",
    "bankName": "MBBank",
    "accountNumber": "0123456789",
    "accountHolder": "NGUYEN VAN A",
    "transferContent": "BETES83920194",
    "createdAt": "2026-11-06T02:30:00Z"
  }
}
```

---

### 2.5. Lấy mã QR thanh toán của đơn hàng
- **Endpoint:** `GET /api/payments/{orderId}/qr`
- **Header:** `Authorization: Bearer <accessToken>`
- **Response `200 OK`:**
```json
{
  "code": 1000,
  "result": {
    "orderId": "672ae4b831fa2a001a111222",
    "orderCode": "BETES83920194",
    "amount": 330000,
    "qrImageUrl": "https://qr.sepay.vn/img?acc=MBBank-0123456789&template=compact&amount=330000&des=BETES83920194",
    "bankId": "MBBank",
    "bankAccount": "0123456789",
    "accountHolder": "NGUYEN VAN A",
    "transferContent": "BETES83920194"
  }
}
```

---

### 2.6. Bảng mã lỗi tổng hợp (Error Code Reference)

| Mã lỗi | Enum Name | HTTP Status | Mô tả & Hướng xử lý |
|---|---|---|---|
| `1001` | `UNAUTHENTICATED` | 401 | Chưa đăng nhập hoặc token không hợp lệ -> Điều hướng về màn hình Login. |
| `1002` | `TOKEN_EXPIRED` | 401 | Access Token hết hạn -> Gọi API `/api/v1/auth/refresh` bằng Refresh Token. |
| `1004` | `TOKEN_REVOKED` | 401 | Phiên làm việc đã đăng xuất -> Xóa token local, về Login. |
| `1005` | `ACCESS_DENIED` | 403 | Không có quyền hoặc tài khoản đã bị admin khóa -> Báo lỗi cho người dùng. |
| `1006` | `BAD_CREDENTIALS` | 401 | Sai tài khoản hoặc mật khẩu. |
| `1007` | `EMAIL_ALREADY_EXISTS`| 409 | Email đã tồn tại trong hệ thống. |
| `1009` | `ACCOUNT_NOT_ACTIVATED` | 403 | **Tài khoản chưa xác thực email -> Điều hướng sang màn hình nhập OTP.** |
| `2001` | `VALIDATION_ERROR` | 400 | Dữ liệu gửi lên sai định dạng hoặc mã OTP không chính xác / hết hạn. |
| `3004` | `INSUFFICIENT_STOCK` | 400 | Hết hàng trong kho khi checkout -> Báo người dùng giảm số lượng trong giỏ. |
| `3005` | `CART_EMPTY` | 400 | Giỏ hàng rỗng. |
| `5002` | `ORDER_CANNOT_BE_CANCELLED` | 400 | Đơn hàng đã ở trạng thái SHIPPING hoặc DELIVERED, không thể hủy. |

---

## 3. CHECKLIST BIẾN MÔI TRƯỜNG DEPLOYMENT (RENDER / CLOUD DOCKER)

Khi triển khai lên môi trường Staging/Production (như Render Free Tier, Docker Compose, Kubernetes), bắt buộc cấu hình các biến sau trong mục **Environment Variables**:

| Tên biến | Bắt buộc | Ví dụ mẫu | Mô tả |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Có | `prod` (hoặc `dev`) | Kích hoạt cấu hình môi trường tương ứng. |
| `PORT` | Không | `8080` (hoặc `10000`) | Cổng ứng dụng lắng nghe (Render tự inject port qua `PORT`). |
| `MONGODB_URI` | **Có** | `mongodb+srv://user:pass@cluster.mongodb.net/ecommerce?retryWrites=true&w=majority` | Chuỗi kết nối MongoDB Atlas. |
| `REDIS_URL` | Tùy chọn | `rediss://default:token@fly-redis.upstash.io:6379` | Chuỗi kết nối Redis Upstash SSL. |
| `REDIS_HOST` | Tùy chọn | `localhost` | Host Redis (nếu không dùng `REDIS_URL`). |
| `REDIS_PORT` | Tùy chọn | `6379` | Port Redis. |
| `REDIS_PASSWORD` | Tùy chọn | `mypassword` | Mật khẩu Redis (để trống nếu local). |
| `REDIS_SSL_ENABLED` | Tùy chọn | `true` | Bật SSL cho Redis Upstash qua mạng internet. |
| `JWT_ACCESS_SECRET` | **Có** | `Chuỗi Base64 >= 32 bytes` | Khóa ký Access Token (sinh bằng: `openssl rand -base64 32`). **Fail-fast nếu thiếu!** |
| `JWT_REFRESH_SECRET` | **Có** | `Chuỗi Base64 >= 32 bytes` | Khóa ký Refresh Token. **Fail-fast nếu thiếu!** |
| `JWT_ACCESS_EXPIRATION_MS` | Không | `3600000` | Thời gian sống Access Token (1 giờ). |
| `JWT_REFRESH_EXPIRATION_MS` | Không | `604800000` | Thời gian sống Refresh Token (7 ngày). |
| `SEPAY_BANK_ID` | **Có** | `MBBank` (hoặc `VCB`, `ACB`, `TPBank`) | Mã định danh ngân hàng nhận tiền. **Fail-fast nếu thiếu!** |
| `SEPAY_BANK_ACCOUNT` | **Có** | `0987654321` | Số tài khoản ngân hàng nhận tiền. **Fail-fast nếu thiếu!** |
| `SEPAY_ACCOUNT_HOLDER` | **Có** | `NGUYEN VAN A` | Tên chủ tài khoản ngân hàng thụ hưởng. **Fail-fast nếu thiếu!** |
| `SEPAY_API_KEY` | **Có*** | `sepay_apikey_12345` | API Key cấu hình trên webhook SePay (`Authorization: Apikey <key>`). |
| `SEPAY_WEBHOOK_SECRET` | **Có*** | `sepay_secret_abcxyz` | Secret key nếu dùng xác thực HMAC-SHA256. |
| `SEPAY_ORDER_CODE_PREFIX` | Không | `BETES` | Tiền tố mã đơn hàng (mặc định: `BETES`). |
| `ORDER_UNPAID_TIMEOUT_MINUTES`| Không | `15` | Thời gian chờ thanh toán trước khi tự động hủy đơn (phút). |
| `SPRING_MAIL_USERNAME` | Tùy chọn | `your_store@gmail.com` | Email gửi OTP. |
| `SPRING_MAIL_PASSWORD` | Tùy chọn | `abcd efgh ijkl mnop` | Gmail App Password (16 ký tự). |
| `CORS_ALLOWED_ORIGINS` | **Có** | `https://shop.mydomain.com,https://admin.mydomain.com` | Danh sách domain Frontend được phép kết nối (phân tách bởi dấu phẩy, **KHÔNG dùng `*`**). |

> *\*Lưu ý:* Bắt buộc cấu hình ít nhất một trong hai (`SEPAY_API_KEY` hoặc `SEPAY_WEBHOOK_SECRET`). Ứng dụng sẽ tự động kích hoạt cơ chế xác thực kép tương ứng.

---

## 4. HƯỚNG DẪN KIỂM THỬ WEBHOOK SEPAY Ở CHẾ ĐỘ TEST (SIMULATION GUIDE)

SePay Webhook hỗ trợ 2 chuẩn xác thực:
1. **Chuẩn 1: SePay API Key** (Header `Authorization: Apikey <key>` - Khuyến nghị chính thức từ SePay).
2. **Chuẩn 2: HMAC-SHA256 Signature** (Header `X-SePay-Signature` kèm `X-SePay-Timestamp`).

### 4.1. Giả lập thanh toán thành công bằng PowerShell (API Key)

Chạy lệnh PowerShell sau tại terminal để giả lập SePay bắn webhook xác nhận chuyển tiền:

```powershell
$apiUrl = "http://localhost:8080/api/payments/sepay-webhook"
$apiKey = "test-sepay-api-key" # Khớp với SEPAY_API_KEY trong cấu hình

# Payload mẫu giả lập SePay chuyển tiền vào tài khoản
$body = @{
    id = 92704
    gateway = "MBBank"
    transactionDate = "2026-11-06 09:30:00"
    accountNumber = "0123456789" # Bắt buộc khớp với SEPAY_BANK_ACCOUNT
    subAccount = $null
    code = $null
    content = "BETES12345678" # Khớp với orderCode của đơn hàng
    transferType = "in"
    transferAmount = 330000   # Bằng hoặc lớn hơn finalAmount
    accumulated = 330000
    referenceCode = "MB123456789"
    description = "Thanh toan don hang BETES12345678"
} | ConvertTo-Json -Compress

# Gửi HTTP POST với Header chuẩn SePay
$response = Invoke-RestMethod -Uri $apiUrl -Method Post `
    -Headers @{
        "Authorization" = "Apikey $apiKey"
        "Content-Type" = "application/json"
    } `
    -Body $body

$response | ConvertTo-Json -Depth 5
```

**Kết quả kỳ vọng trả về:**
```json
{
  "success": true,
  "message": "Xử lý webhook SePay thành công",
  "orderCode": "BETES12345678",
  "status": "PROCESSED"
}
```
Đồng thời đơn hàng `BETES12345678` trong cơ sở dữ liệu sẽ tự động chuyển sang `paymentStatus = "PAID"` và `status = "CONFIRMED"`.

---

### 4.2. Giả lập thanh toán bằng cURL (HMAC-SHA256 Signature)

Nếu SePay được cấu hình ở chế độ HMAC-SHA256:

```powershell
$apiUrl = "http://localhost:8080/api/payments/sepay-webhook"
$secret = "test-webhook-secret" # Khớp với SEPAY_WEBHOOK_SECRET
$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()

$rawBody = '{"id":92705,"gateway":"MBBank","transactionDate":"2026-11-06 09:35:00","accountNumber":"0123456789","content":"BETES12345678","transferType":"in","transferAmount":330000,"referenceCode":"MB99999"}'

# Sinh chữ ký HMAC-SHA256 theo chuẩn SePay: timestamp + "." + rawBody
$dataToSign = "$timestamp.$rawBody"
$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($secret)
$hash = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($dataToSign))
$signature = "sha256=" + [BitConverter]::ToString($hash).Replace("-", "").ToLower()

# Gửi request
Invoke-RestMethod -Uri $apiUrl -Method Post `
    -Headers @{
        "X-SePay-Signature" = $signature
        "X-SePay-Timestamp" = "$timestamp"
        "Content-Type" = "application/json"
    } `
    -Body $rawBody
```

---

### 4.3. Các trường hợp biên (Edge Cases) đã được kiểm thử tự động
1. **Gửi thiếu hoặc sai API Key / Signature:** Trả về ngay lập tức `HTTP 401 Unauthorized` (không sửa DB, không log rò rỉ secret).
2. **Bắn webhook trùng lặp (Idempotency):** Trả về `HTTP 200 OK` với thông báo `"Giao dịch SePay ID ... đã được xử lý trước đó"` mà không cập nhật lại đơn hàng lần hai.
3. **Tiền chuyển vào nhỏ hơn giá trị đơn (Thiếu tiền - Underpaid):** Giao dịch được lưu lại với trạng thái `MISMATCH`, cờ `isUnderpaid = true`, đơn hàng vẫn giữ nguyên `UNPAID` để chờ đối soát kế toán.
4. **Tiền chuyển vào lớn hơn giá trị đơn (Thừa tiền - Overpaid):** Giao dịch lưu lại với trạng thái `MISMATCH`, cờ `isOverpaid = true`, lưu lại `amountDiscrepancy`, đơn hàng được chuyển sang `PAID` và cảnh báo kế toán hoàn trả số dư thừa.
5. **Đơn hàng đã bị hủy hoặc hết hạn:** Giao dịch ghi nhận trạng thái `FAILED` kèm lý do lỗi, không mở lại đơn hàng đã kết thúc.

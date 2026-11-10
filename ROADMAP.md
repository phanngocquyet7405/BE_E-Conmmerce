# Lộ Trình Triển Khai 4 Module Nghiệp Vụ Bàn Giao Cho FE & Mobile Dev

Tài liệu này thuyết minh và hướng dẫn chi tiết theo đúng lộ trình tuần tự:
**Cart (Giỏ hàng) ➔ Profile & Address (Hồ sơ & Sổ địa chỉ) ➔ Checkout & Order (Đặt hàng & Xử lý đơn) ➔ Search & Advanced Filter (Tìm kiếm & Bộ lọc nâng cao)**.

Toàn bộ mã nguồn thực thi đã được tích hợp đầy đủ vào gói dự án `ecommerce-backend.zip`.

---

## Bước 1: Module Cart (Quản Lý Giỏ Hàng)

### 1.1. Mục tiêu & Luồng hoạt động cho FE/Mobile
- Giỏ hàng gắn liền với từng User qua `userId` (`GET /api/v1/cart`).
- Cho phép thêm sản phẩm vào giỏ (`POST /api/v1/cart/items`), tăng/giảm số lượng (`PUT /api/v1/cart/items`), xóa từng món (`DELETE /api/v1/cart/items/{productId}`) hoặc làm trống giỏ (`DELETE /api/v1/cart/clear`).
- **Xử lý nghiệp vụ**:
  - Tự động kiểm tra số lượng tồn kho (`stockQuantity`) trước khi cho phép thêm hoặc tăng số lượng; nếu thiếu, hệ thống ném mã lỗi `3004 - INSUFFICIENT_STOCK`.
  - Tự động cập nhật lại đơn giá (`unitPrice`) mới nhất từ bảng Product và tính tổng tiền (`totalAmount`) theo thời gian thực.

### 1.2. Danh sách Endpoints
| Phương thức | Đường dẫn API | Quyền truy cập | Mô tả chức năng |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/cart` | Authenticated | Lấy chi tiết giỏ hàng và tổng tiền |
| `POST` | `/api/v1/cart/items` | Authenticated | Thêm sản phẩm vào giỏ hàng |
| `PUT` | `/api/v1/cart/items` | Authenticated | Cập nhật số lượng sản phẩm trong giỏ |
| `DELETE` | `/api/v1/cart/items/{productId}` | Authenticated | Xóa 1 món hàng khỏi giỏ |
| `DELETE` | `/api/v1/cart/clear` | Authenticated | Xóa toàn bộ giỏ hàng |

---

## Bước 2: Module Profile & Address Book (Hồ Sơ & Sổ Địa Chỉ Giao Hàng)

### 2.1. Mục tiêu & Luồng hoạt động cho FE/Mobile
- Mobile App cần cho phép khách hàng lưu nhiều địa chỉ nhận hàng (ví dụ: Nhà riêng, Công ty) và chọn 1 địa chỉ mặc định (`isDefault: true`).
- Hỗ trợ đổi mật khẩu an toàn (`POST /api/v1/profile/change-password`): Yêu cầu mật khẩu cũ khớp với BCrypt hash trước khi cho phép cập nhật.

### 2.2. Danh sách Endpoints
| Phương thức | Đường dẫn API | Quyền truy cập | Mô tả chức năng |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/profile/me` | Authenticated | Lấy thông tin cá nhân và danh sách địa chỉ |
| `PUT` | `/api/v1/profile/me` | Authenticated | Cập nhật họ tên, số điện thoại, avatar URL |
| `POST` | `/api/v1/profile/change-password` | Authenticated | Đổi mật khẩu tài khoản |
| `GET` | `/api/v1/profile/addresses` | Authenticated | Xem danh sách các địa chỉ nhận hàng đã lưu |
| `POST` | `/api/v1/profile/addresses` | Authenticated | Thêm mới địa chỉ nhận hàng |
| `PUT` | `/api/v1/profile/addresses/{addressId}` | Authenticated | Chỉnh sửa thông tin địa chỉ |
| `DELETE` | `/api/v1/profile/addresses/{addressId}` | Authenticated | Xóa địa chỉ |
| `PUT` | `/api/v1/profile/addresses/{addressId}/default` | Authenticated | Đặt làm địa chỉ nhận hàng mặc định |

---

## Bước 3: Module Checkout & Order (Đặt Hàng & Xử Lý Đơn Hàng)

### 3.1. Mục tiêu & Vòng đời đơn hàng (State Machine)
- **Tạo đơn hàng (`POST /api/v1/orders`)**:
  - Khách hàng chỉ cần truyền `addressId`, `paymentMethod` (COD, VNPAY, MOMO) và ghi chú `note`.
  - Hệ thống lấy danh sách món từ Giỏ hàng của User, kiểm tra tồn kho lần cuối, **trừ trực tiếp tồn kho của từng sản phẩm trong MongoDB**, snapshot lại giá và thông tin người nhận tại thời điểm đặt, tạo mã đơn hàng duy nhất dạng `ORD-{timestamp}-{uuid}`, và dọn sạch giỏ hàng.
- **Hủy đơn hàng (`PUT /api/v1/orders/{id}/cancel`)**:
  - Khách hàng chỉ được phép hủy khi đơn ở trạng thái `PENDING`.
  - Khi hủy thành công, hệ thống **tự động hoàn trả lại số lượng tồn kho** cho các sản phẩm.
- **Vòng đời chuyển trạng thái (Dành cho Admin/Chủ shop)**:
  `PENDING` (Chờ duyệt) ➔ `CONFIRMED` (Đã xác nhận) ➔ `SHIPPING` (Đang giao) ➔ `DELIVERED` (Đã giao thành công).

### 3.2. Danh sách Endpoints
| Phương thức | Đường dẫn API | Quyền truy cập | Mô tả chức năng |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/orders` | Customer | Đặt hàng từ giỏ hàng hiện tại |
| `GET` | `/api/v1/orders` | Customer | Xem lịch sử đơn hàng của bản thân (phân trang) |
| `GET` | `/api/v1/orders/{id}` | Customer | Xem chi tiết 1 đơn hàng |
| `PUT` | `/api/v1/orders/{id}/cancel` | Customer | Hủy đơn hàng (khi đang PENDING) |
| `GET` | `/api/v1/admin/orders` | Admin | Quản trị viên xem toàn bộ đơn hàng (lọc theo status) |
| `PUT` | `/api/v1/admin/orders/{id}/status` | Admin | Quản trị viên cập nhật trạng thái đơn hàng |

---

## Bước 4: Module Search & Advanced Filter (Tìm Kiếm & Bộ Lọc Nâng Cao)

### 4.1. Mục tiêu & Tối ưu cho Mobile / Frontend
- Hỗ trợ thanh tìm kiếm toàn diện (`keyword`): Tìm kiếm không phân biệt hoa thường (Regex Case-insensitive) trên cả `name`, `description` và mã `sku`.
- Kết hợp đa điều kiện:
  - Lọc theo danh mục: `categoryId`.
  - Lọc theo khoảng giá: `minPrice` và `maxPrice`.
  - Sắp xếp linh hoạt: `sortBy` (`createdAt`, `price`, `name`), `sortDir` (`asc`, `desc`).
- Phân trang chuẩn mực: Trả về `PageResponse<T>` gồm `pageNo`, `pageSize`, `totalElements`, `totalPages` và `last` (hỗ trợ Mobile làm Infinite Scroll mượt mà).

### 4.2. Danh sách Endpoints
| Phương thức | Đường dẫn API | Quyền truy cập | Tham số Query hỗ trợ |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/public/products/search` | Public | `keyword`, `categoryId`, `minPrice`, `maxPrice`, `page`, `size`, `sortBy`, `sortDir` |

---

## 5. Hướng Phát Triển Tiếp Theo (Kế Hoạch Sau Đợt Này - Đề Xuất & Nghiên Cứu)

> **Lưu ý quan trọng**: Mục này đóng vai trò định hướng kiến trúc và kế hoạch phát triển kỹ thuật trong tương lai. **KHÔNG** hiện thực mã nguồn trong giai đoạn hiện tại. Mọi đề xuất đã được sàng lọc và đánh giá tính khả thi dựa trên giới hạn môi trường vận hành thực tế (Cloud Container 512MB RAM, CPU Shared, Single-Seller).

---

### 5.1. Nhóm Bảo Mật Tài Khoản & Quản Lý Phiên (Account Security)

| STT | Hạng mục | Mô tả kỹ thuật | Căn cứ hiện trạng / Rationale | Độ phức tạp | Phụ thuộc | Rủi ro & Thách thức | Ưu tiên |
| :--- | :--- | :--- | :--- | :---: | :--- | :--- | :---: |
| 1 | **Cloudflare Turnstile (Captcha)** | Tích hợp widget Turnstile tại form đăng ký và đăng nhập ở Frontend; Backend verify token với Cloudflare Siteverify API trước khi xử lý OTP/mật khẩu. | RateLimitFilter (`RateLimitFilter.java:38`) hiện chặn theo IP, dễ bị vượt qua nếu bot xoay proxy/IPv6 hoặc ảnh hưởng người dùng chung mạng NAT. | **S** | Cloudflare Secret Key | Trễ thêm ~200ms gọi Cloudflare HTTP API khi đăng ký/đăng nhập. | **Cao** |
| 2 | **Social Login (OAuth2 Google / Facebook)** | Hỗ trợ đăng nhập một chạm qua OpenID Connect / OAuth2. Tự động liên kết tài khoản theo email đã xác thực. | Hiện tại chỉ có xác thực qua email/mật khẩu truyền thống (`AuthServiceImpl.java:48`). | **M** | Google/Facebook Cloud Console App credentials | Xung đột nếu email người dùng chưa xác thực bên mạng xã hội; cần cơ chế merge account chặt chẽ. | **Trung bình** |
| 3 | **TOTP 2FA cho Quản trị viên (Admin)** | Kích hoạt mã OTP 6 số qua Google Authenticator / Authy bằng chuẩn RFC 6238 cho các tài khoản có role `ROLE_ADMIN`. | `SecurityConfig.java:83` bảo vệ route admin chỉ bằng JWT role, rủi ro cao nếu lộ thông tin đăng nhập quản trị viên. | **M** | Thư viện TOTP (java-otp hoặc tương đương) | Cần luồng backup recovery codes phòng khi Admin mất thiết bị xác thực. | **Cao** |
| 4 | **Device & Session Management (Redis JTI Sets)** | Bổ sung claim `jti` (JWT ID) độc bản vào Access & Refresh Token (`JwtUtil.java:45`). Lưu danh sách active JTI theo `userId` dạng Redis Set kèm metadata (User-Agent, IP, thời điểm đăng nhập). | `RedisTokenServiceImpl.java:34` hiện phải scan pattern `refresh_token:userId:*`. Quản lý tập hợp JTI giúp truy vấn danh sách thiết bị đang đăng nhập O(1) và cho phép người dùng chủ động đá từng thiết bị cụ thể. | **M** | Redis Lettuce connection | Tăng dung lượng lưu trữ trên Redis; cần đảm bảo TTL của key tự dọn rác đúng thời hạn. | **Trung bình** |
| 5 | **Email Change Flow (Đổi Email kèm xác thực 2 chiều)** | Cho phép người dùng đổi email đăng nhập qua luồng: Xác thực mật khẩu -> Gửi mã xác nhận về email mới -> Khớp OTP thành công mới cập nhật DB. | Hiện tại `ProfileServiceImpl.java` chưa hỗ trợ đổi email, email đang đóng vai trò định danh bất biến (`User.java:23`). | **M** | `OtpThrottleService`, `EmailService` | Chạy đua nếu email mới bị trùng với user khác đang đăng ký; cần kiểm tra tồn tại nguyên tử. | **Trung bình** |
| 6 | **Xóa Tài Khoản & Tuân Thủ Nghị Định 13/2023/NĐ-CP** | API cho phép khách hàng yêu cầu xóa tài khoản cá nhân (Right to erasure). Ẩn danh hóa (anonymize) dữ liệu cá nhân trong đơn hàng cũ để bảo lưu nghĩa vụ kế toán thuế. | Hiện tại hệ thống chưa có API xóa tài khoản (`UserController`/`ProfileController`). | **M** | `OrderRepository`, `UserRepository` | Phải bảo lưu thông tin đơn hàng đã xuất hóa đơn/thanh toán, không được xóa vật lý Order liên quan đến doanh thu thuế. | **Thấp** |
| 7 | **Cảnh Báo Đăng Nhập Thiết Bị Lạ (Login Alerts)** | Ghi nhận vân tay thiết bị (IP subnet, hệ điều hành từ User-Agent). Gửi email thông báo cho chủ tài khoản khi phát hiện đăng nhập từ vị trí/thiết bị bất thường. | Ý tưởng nâng cao trải nghiệm an ninh; chưa có cơ chế phát hiện thiết bị trong `AuthServiceImpl.java:125`. | **S** | `EmailService`, Redis | Có thể gây phiền toái (false positive) nếu người dùng thường xuyên dùng mạng 4G đổi IP liên tục. | **Thấp** |
| 8 | **JWT Key Rotation (`kid` Header)** | Hỗ trợ cấu hình danh sách khóa ký JWT với định danh `kid` trên Header JWT; cho phép xoay vòng khóa ký định kỳ mà không làm vô hiệu hóa token cũ còn hạn. | `JwtUtil.java:29` đang đọc một bí mật cố định `${JWT_SECRET}`. Khi thay khóa buộc toàn bộ người dùng phải đăng nhập lại. | **M** | Cấu hình Secret Vault / Multi-key config | Logic parse JWT phức tạp hơn, cần fallback key cũ mượt mà. | **Thấp** |
| 9 | **Security Audit Log Tập Trung** | Lưu vết toàn bộ hành vi nhạy cảm (đổi quyền Admin, khóa tài khoản, đăng xuất tất cả, đối soát thủ công SePay) vào collection MongoDB `audit_logs` có chỉ mục tìm kiếm. | Hiện tại chỉ log qua `log.info()` ra console (`UserServiceImpl.java:128`, `PaymentServiceImpl.java:140`), dễ trôi mất log trên container ephemeral. | **S** | MongoDB Collection & Index | Tăng dung lượng database; cần thiết lập TTL Index (ví dụ 90 ngày) tự dọn log cũ. | **Trung bình** |
| 10 | **Kiểm Tra Mật Khẩu Bị Rò Rỉ (HaveIBeenPwned API)** | Khi đổi mật khẩu hoặc đăng ký, hash 5 ký tự đầu SHA-1 của mật khẩu và kiểm tra qua Pwned Passwords API theo chuẩn k-Anonymity để chặn mật khẩu từng bị lộ. | `PasswordPolicyServiceImpl.java:15` chỉ kiểm tra độ dài và regex ký tự, chưa ngăn chặn được các mật khẩu phổ biến bị rò rỉ trên mạng. | **S** | HaveIBeenPwned API công khai (miễn phí, không cần API key) | Phụ thuộc mạng ngoài; bắt buộc thiết kế fail-open với timeout ngắn (1s) để không làm treo luồng đăng ký khi mạng chậm. | **Thấp** |

---

### 5.2. Nhóm Hạ Tầng Kỹ Thuật & Tối Ưu Hóa Nền Tảng (Platform & Architecture)

| STT | Hạng mục | Mô tả kỹ thuật | Căn cứ hiện trạng / Rationale | Độ phức tạp | Phụ thuộc | Rủi ro & Thách thức | Ưu tiên |
| :--- | :--- | :--- | :--- | :---: | :--- | :--- | :---: |
| 1 | **Idempotency-Key Header cho API Checkout** | Header `X-Idempotency-Key` (UUIDv4) gửi kèm `POST /api/v1/orders`. Redis lưu key trạng thái xử lý trong 120s; chặn tức thì request gửi đúp do lag mạng hay click nhanh. | `OrderServiceImpl.java:46` có concurrency check tồn kho nhưng nếu client click đúp vẫn có thể tạo 2 đơn hàng liên tiếp nếu kho còn đủ. | **S** | Redis | Cần xử lý kịch bản client retry khi request đầu đang thực thi (trả HTTP 409 hoặc 425). | **Cao** |
| 2 | **Cơ chế Xóa Mềm (Soft Delete)** | Thêm cờ `deleted: boolean` và `deleted_at: Instant` vào Document `Category`, `Brand`, `Product`. Mọi truy vấn đọc tự động lọc `{ deleted: { $ne: true } }`. | `Category.java`, `Brand.java` hiện chưa có cờ xóa mềm; nếu xóa vật lý danh mục có thể làm mồ côi (orphaned) các sản phẩm liên kết. | **M** | MongoDB Queries / Repositories | Cần rà soát toàn bộ query `findBy...` tránh lộ dữ liệu đã xóa mềm; đánh chỉ mục compound index kèm `deleted`. | **Trung bình** |
| 3 | **Spring Boot Actuator & Correlation-ID Tracing** | Tích hợp `TraceIdFilter` sinh `X-Correlation-Id` cho mỗi request HTTP, gán vào MDC logging của Logback; kích hoạt endpoint `/actuator/health` và `/actuator/metrics`. | `HealthController.java:16` hiện tại chỉ kiểm tra thủ công; khó debug lỗi phân tán khi kết nối từ Next.js qua Backend. | **S** | Spring Boot Actuator, Logback MDC | Phải cấu hình bảo mật chặn public các endpoint nhạy cảm (`/env`, `/heapdump`) của Actuator. | **Trung bình** |
| 4 | **Testcontainers Integration Tests** | Xây dựng bộ kiểm thử tích hợp (Integration Test) chạy MongoDB và Redis trên container Docker thật trong quá trình build Maven. | Toàn bộ 169 unit test hiện tại sử dụng `@ExtendWith(MockitoExtension.class)`. Mock không chứng minh được tương thích phiên bản engine MongoDB/Redis thật. | **M** | Docker runtime trên máy dev / CI runner | Thời gian chạy test tăng từ vài giây lên ~45 giây; yêu cầu môi trường có Docker daemon. | **Cao** |
| 5 | **CI/CD Pipeline với GitHub Actions** | Tự động kích hoạt build `mvn clean test`, kiểm tra code format, quét lỗ hổng bảo mật dependency mỗi khi tạo Pull Request vào nhánh `main`. | Dự án đang phụ thuộc vào việc chạy test thủ công trên máy cục bộ trước khi push. | **S** | GitHub Repository Settings | Cần cấp các biến môi trường giả lập (dummy secrets) trong GitHub Actions để pass test context. | **Cao** |
| 6 | **MongoDB TTL Index cho Unverified Users** | Tạo index TTL trên trường `created_at` trong collection `users` với điều kiện lọc `{ active: false }`, tự động xóa tài khoản sau thời hạn `unverified-retention-hours`. | `UnverifiedUserCleanupTask.java:23` đang chạy Cron mỗi giờ, chiếm CPU và RAM quét database theo chu kỳ. MongoDB TTL Index giải phóng hoàn toàn tác vụ chạy nền này. | **S** | MongoDB Atlas Engine Index | TTL Index của MongoDB chạy ngầm theo chu kỳ ~60s của database engine; cần cẩn trọng nếu tài khoản kích hoạt muộn. | **Cao** |
| 7 | **Transactional Outbox Pattern cho Email** | Lưu sự kiện cần gửi email (OTP, thông báo đổi mật khẩu, xác nhận đơn hàng) vào collection MongoDB `outbox_events` cùng transaction nghiệp vụ; một worker ngầm đọc và gửi đi. | `EmailServiceImpl.java:31` hiện gửi email đồng bộ trực tiếp; nếu mạng SMTP chậm sẽ làm chậm response của API `/register` và `/change-password`. | **M** | MongoDB ReplicaSet Transactions | Phải xử lý retry khi gửi mail thất bại, quản lý trạng thái DEAD-LETTER của email hỏng. | **Trung bình** |
| 8 | **Token Bucket Rate Limiting qua Redis Lua Script** | Chuyển đổi logic rate limiting sang kịch bản Redis Lua Script nguyên tử (atomic 1 RTT). | `RateLimitFilter.java:38` hiện thực hiện nhiều lệnh Redis tuần tự (`opsForValue().increment`, `getExpire`), tiềm ẩn độ trễ mạng tích lũy. | **S** | Redis Lua Engine (EVAL / EVALSHA) | Cần kiểm tra tương thích lệnh EVAL trên Upstash Redis Serverless REST proxy nếu chuyển đổi. | **Trung bình** |
| 9 | **OpenAPI / Swagger Contract Testing** | Sinh tài liệu OpenAPI 3.0 tự động từ mã nguồn và kiểm thử tính tương thích (contract breaking change detection) giữa Backend và Frontend TypeScript Client. | Giảm thiểu tối đa sai lệch thông tin giữa tài liệu bàn giao (`FRONTEND_HANDOVER.md`) và thực tế code. | **S** | Springdoc OpenAPI UI | Cần cấu hình ẩn các trường nhạy cảm trong schema DTO. | **Thấp** |

---

### 5.3. Nhóm Tính Năng Người Dùng & Thương Mại Điện Tử (E-Commerce Features)

| STT | Hạng mục | Mô tả kỹ thuật | Căn cứ hiện trạng / Rationale | Độ phức tạp | Phụ thuộc | Rủi ro & Thách thức | Ưu tiên |
| :--- | :--- | :--- | :--- | :---: | :--- | :--- | :---: |
| 1 | **Voucher & Discount Engine (Mã Khuyến Mãi)** | Quản lý coupon: Giảm theo % hoặc số tiền cố định, giới hạn tổng lượt dùng, giá trị đơn hàng tối thiểu, hạn sử dụng. Áp dụng nguyên tử khi checkout. | `OrderServiceImpl.java:46` hiện tính giá trực tiếp từ sản phẩm và phí ship cố định, chưa có cơ chế giảm giá. | **M** | Collection `vouchers`, Concurrency Lock | Race condition khi nhiều người dùng cùng áp dụng 1 voucher có số lượng giới hạn cuối cùng. | **Cao** |
| 2 | **Product Reviews & Ratings (Đánh Giá & Nhận Xét)** | Khách hàng đã mua sản phẩm (đơn hàng ở trạng thái `DELIVERED`) được phép đánh giá 1-5 sao và viết nhận xét kèm hình ảnh. Tính rating trung bình trên Product. | `Product.java` hiện chưa có trường đánh giá; khách hàng chưa có kênh phản hồi chất lượng hàng. | **M** | Collection `reviews`, `MediaService` | Spam đánh giá ảo; cần kiểm tra nghiêm ngặt quyền đánh giá theo lịch sử đơn hàng đã giao (`DELIVERED`). | **Trung bình** |
| 3 | **Wishlist (Danh Sách Sản Phẩm Yêu Thích)** | Cho phép khách hàng thả tim lưu sản phẩm quan tâm để xem lại sau (`POST /api/v1/wishlist/{productId}`). | Tăng tỷ lệ chuyển đổi khách hàng quay lại mua sắm. | **S** | Collection `wishlists` hoặc mảng nhúng trong User | Cần xử lý khi sản phẩm trong wishlist bị vô hiệu hóa hoặc hết hàng. | **Trung bình** |
| 4 | **Tính Phí Vận Chuyển Động (Dynamic Shipping Fee API)** | Tích hợp API đối tác vận chuyển (Giao Hàng Nhanh / Viettel Post) tính cước phí thực tế dựa trên trọng lượng, kích thước và địa chỉ nhận hàng của khách. | `sampleOrder.shippingFee` hiện đang fix cứng 30.000 VNĐ (`PaymentServiceTest.java:52`), không phản ánh cước phí thực tế theo khoảng cách. | **M** | API bên thứ 3 (GHN / ViettelPost) | Phụ thuộc độ sẵn sàng của hệ thống đối tác; cần có cơ chế fallback giá cước cố định khi API đối tác timeout. | **Trung bình** |
| 5 | **Order Timeline Tracking (Nhật Ký Tiến Trình Đơn)** | Ghi lại lịch sử từng bước chuyển trạng thái của đơn hàng kèm thời gian và ghi chú (`order_timeline`), hiển thị cho khách hàng theo dõi trực quan. | `Order.java:35` hiện chỉ lưu 1 trạng thái hiện tại (`OrderStatus status`), thiếu lịch sử chi tiết thời điểm chuyển trạng thái. | **S** | Nhúng mảng `List<OrderTimelineEvent>` trong `Order` | Rất an toàn, dễ hiện thực, tăng trải nghiệm người dùng trên Mobile App. | **Cao** |
| 6 | **Thông Báo Thời Gian Thực (Realtime Server-Sent Events)** | Thiết lập kênh SSE (`GET /api/v1/notifications/stream`) để đẩy thông báo trạng thái đơn hàng và xác nhận tiền SePay về máy khách ngay lập tức. | Hiện tại Frontend phải polling định kỳ để biết trạng thái thanh toán đơn hàng SePay (`PaymentController.java`). | **M** | Spring WebMvc SseEmitter | Giữ kết nối HTTP liên tục có thể chiếm tài nguyên thread và socket descriptor trên container 512MB RAM. | **Thấp** |
| 7 | **MongoDB Atlas Search (Full-Text Search)** | Kích hoạt bộ máy Lucene tích hợp sẵn trên MongoDB Atlas cho collection `products`, hỗ trợ tìm kiếm mờ (fuzzy search), tự động sửa lỗi chính tả (typo tolerance). | `ProductServiceImpl.java:62` hiện tìm kiếm bằng Regex `Pattern.quote()`, không tận dụng được scoring xếp hạng mức độ liên quan. | **S** | MongoDB Atlas Search Index | Chỉ hoạt động trên MongoDB Atlas Cloud, không chạy được trên MongoDB Community cục bộ chạy Docker đơn giản. | **Trung bình** |

---

### 5.4. Đánh Giá Khả Thi & Các Đề Xuất Bị Loại Trừ (Feasibility Evaluation & Exclusions)

Nhằm đảm bảo hệ thống vận hành bền bỉ trên môi trường Cloud hạn chế (Render Free Tier 512MB RAM, CPU Shared, Cold Start 15 phút), các kiến trúc sau đây **BỊ LOẠI TRỪ KHỎI ROADMAP**:

1. **Loại trừ Message Broker nặng (Apache Kafka / RabbitMQ):**
   - *Lý do:* Kafka và RabbitMQ tiêu tốn tối thiểu 1GB - 2GB RAM để chạy broker và Zookeeper/KRaft, hoàn toàn không tương thích với giới hạn 512MB RAM của gói máy chủ hiện tại.
   - *Giải pháp thay thế:* Sử dụng MongoDB Transactional Outbox kết hợp Spring Scheduled Worker hoặc Redis Streams nhẹ nhàng.
2. **Loại trừ Kiến trúc Phân Tán Đa Dịch Vụ (Microservices Architecture):**
   - *Lý do:* Dự án phục vụ mô hình kinh doanh Single-Seller (Cửa hàng độc lập). Chi phí quản lý mạng, service mesh, API Gateway riêng biệt và phân mảnh dữ liệu phân tán sẽ gây quá tải vận hành và tài nguyên hạ tầng.
   - *Giải pháp thay thế:* Duy trì kiến trúc Modular Monolith với 3-Tier chuẩn mực, chia tách các module logic rõ ràng qua Interface và Package.
3. **Loại trừ Cụm Tìm Kiếm Rời (Elasticsearch / Solr Cluster):**
   - *Lý do:* JVM của Elasticsearch đòi hỏi tối thiểu 2GB heap RAM để vận hành ổn định.
   - *Giải pháp thay thế:* Tận dụng MongoDB Atlas Search (Lucene tích hợp trực tiếp trên database cluster của Atlas) không làm tăng dung lượng RAM của ứng dụng Backend.

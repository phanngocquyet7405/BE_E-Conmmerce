# BÁO CÁO TỔNG HỢP: SỬA LỖI SECURITYCONFIG, TEST CONTEXT VÀ KẾT QUẢ KIỂM THỬ THỦ CÔNG

---

## I. KẾT QUẢ BƯỚC 1: SỬA LỖI THỨ TỰ FILTER TRONG SECURITYCONFIG

### 1. Phân tích nguyên nhân
Khi khởi động ứng dụng trên môi trường Production, Spring Boot dừng với ngoại lệ:
```text
Caused by: java.lang.IllegalArgumentException: The Filter class com.ecommerce.security.JwtAuthenticationFilter does not have a registered order
	at org.springframework.security.config.annotation.web.builders.HttpSecurity.addFilterAtOffsetOf(HttpSecurity.java:3297)
	at org.springframework.security.config.annotation.web.builders.HttpSecurity.addFilterBefore(HttpSecurity.java:3290)
	at com.ecommerce.config.SecurityConfig.securityFilterChain(SecurityConfig.java:83)
```
- **Nguyên nhân cốt lõi**: Trong cơ chế cấu hình bộ lọc của Spring Security, khi gọi phương thức `.addFilterBefore(filter, targetFilterClass)`, đối số `targetFilterClass` bắt buộc phải là một filter đã có thứ tự được ghi nhận trong bảng thứ tự (`FilterOrderRegistration`).
- **Lỗi trong code cũ**:
  ```java
  // LỖI: JwtAuthenticationFilter.class là custom filter và chưa được nạp vào chain,
  // nên Spring Security chưa có vị trí order của nó -> Ném IllegalArgumentException.
  .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class)
  .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
  ```

### 2. Giải pháp thực hiện
Đảo ngược thứ tự 2 dòng đăng ký filter:
1. Đăng ký `jwtAuthenticationFilter` trước `UsernamePasswordAuthenticationFilter.class`. Vì `UsernamePasswordAuthenticationFilter` là filter tích hợp sẵn của Spring Security (đã có order cố định), việc này hợp lệ và đồng thời ghi nhận thứ tự của `JwtAuthenticationFilter` vào `FilterOrderRegistration`.
2. Tiếp theo mới đăng ký `rateLimitFilter` trước `JwtAuthenticationFilter.class`. Lúc này `JwtAuthenticationFilter` đã có thứ tự sẵn sàng, Spring Security định vị được vị trí để xếp `rateLimitFilter` đứng trước nó.

```java
  // ĐÃ SỬA: Đăng ký JwtAuthenticationFilter trước, sau đó đăng ký RateLimitFilter trước JwtAuthenticationFilter
  .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
  .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);
```

### 3. Git Diff chi tiết tại [SecurityConfig.java](file:///c:/Users/84862/Desktop/WorkSpace/ecommerce-backend/src/main/java/com/ecommerce/config/SecurityConfig.java)
```diff
diff --git a/src/main/java/com/ecommerce/config/SecurityConfig.java b/src/main/java/com/ecommerce/config/SecurityConfig.java
index 93c0b86..0c9d0d2 100644
--- a/src/main/java/com/ecommerce/config/SecurityConfig.java
+++ b/src/main/java/com/ecommerce/config/SecurityConfig.java
@@ -80,8 +80,8 @@ public class SecurityConfig {
                 // 4. Các yêu cầu còn lại
                 .anyRequest().authenticated()
             )
-            .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class)
-            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
+            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
+            .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);
 
         return http.build();
     }
```

---

## II. KẾT QUẢ BƯỚC 4: THÊM TEST `@SpringBootTest` BẢO VỆ CONTEXT & FILTER CHAIN

### 1. Mục tiêu
Thêm kiểm thử nạp Spring Context kết hợp `SecurityFilterChain` để đảm bảo lỗi cấu hình thứ tự Filter tương tự sẽ bị phát hiện và ngăn chặn ngay ở giai đoạn build/test (`mvn verify`).

### 2. Mã nguồn kiểm thử: [EcommerceApplicationTests.java](file:///c:/Users/84862/Desktop/WorkSpace/ecommerce-backend/src/test/java/com/ecommerce/EcommerceApplicationTests.java)
```java
package com.ecommerce;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.SecurityFilterChain;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class EcommerceApplicationTests {

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    @DisplayName("Kiểm tra Spring context và SecurityFilterChain khởi tạo thành công")
    void contextLoads() {
        assertNotNull(securityFilterChain, "SecurityFilterChain phải được khởi tạo thành công");
    }
}
```

### 3. Kết quả xác minh hai chiều
1. **Xác nhận bắt lỗi (Fault Verification)**:
   - Revert tạm thời về thứ tự filter lỗi cũ và chạy `mvn test -Dtest=EcommerceApplicationTests`.
   - Kết quả: Build thất bại chính xác với ngoại lệ:
     ```text
     Caused by: java.lang.IllegalArgumentException: The Filter class com.ecommerce.security.JwtAuthenticationFilter does not have a registered order
     [ERROR] Errors: 
     [ERROR]   EcommerceApplicationTests.contextLoads » IllegalState Failed to load ApplicationContext
     [INFO] BUILD FAILURE
     ```
2. **Xác nhận sau khi sửa (Fix Verification)**:
   - Chạy kiểm thử toàn diện `mvn verify`.
   - Kết quả: Tất cả 13/13 test cases đều hoàn thành thành công:
     ```text
     [INFO] Running com.ecommerce.EcommerceApplicationTests
     [INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 8.108 s -- in com.ecommerce.EcommerceApplicationTests
     [INFO] Running com.ecommerce.security.UserBlockMechanismTest
     [INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.162 s -- in com.ecommerce.security.UserBlockMechanismTest
     [INFO] Running com.ecommerce.service.OrderServiceTest
     [INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.698 s -- in com.ecommerce.service.OrderServiceTest
     [INFO] Running com.ecommerce.util.JwtUtilTest
     [INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.304 s -- in com.ecommerce.util.JwtUtilTest
     [INFO] Results:
     [INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
     [INFO] BUILD SUCCESS
     ```

---

## III. PHÂN TÍCH CODE LIÊN QUAN ĐẾN REDIS VÀ RATE LIMIT

Dựa trên việc đọc mã nguồn tại [`RedisTokenServiceImpl.java`](file:///c:/Users/84862/Desktop/WorkSpace/ecommerce-backend/src/main/java/com/ecommerce/service/impl/RedisTokenServiceImpl.java), [`RedisConfig.java`](file:///c:/Users/84862/Desktop/WorkSpace/ecommerce-backend/src/main/java/com/ecommerce/config/RedisConfig.java), [`RateLimitFilter.java`](file:///c:/Users/84862/Desktop/WorkSpace/ecommerce-backend/src/main/java/com/ecommerce/security/RateLimitFilter.java), [`JwtAuthenticationFilter.java`](file:///c:/Users/84862/Desktop/WorkSpace/ecommerce-backend/src/main/java/com/ecommerce/security/JwtAuthenticationFilter.java):

### 1. Tên key, TTL và Serializer của `blockUser`
- **Tên key**: Được định nghĩa bằng `private static final String BLOCKED_USER_PREFIX = "blocked:user:";` ghép với `userId` $\rightarrow$ Chuỗi chính xác là: **`blocked:user:{userId}`**.
- **TTL**: Tính bằng `appProperties.getJwt().getAccessExpirationMs()` (mặc định cấu hình tại `application-dev.yml` là 3,600,000 ms) $\rightarrow$ **TTL = 3600 giây (1 giờ)**.
- **Serializer**: Bean `redisTemplate` trong `RedisConfig` cấu hình `keySerializer` là `StringRedisSerializer`. Serializer này chuyển đổi trực tiếp `String` thành UTF-8 bytes mà không gắn thêm tiền tố, nhãn lớp hay ký tự phân tách nhị phân nào. Tên key lưu trong Redis chính xác 100% là `blocked:user:{id}`.

### 2. Loại Template, DB index và Địa chỉ kết nối
- **Template sử dụng**:
  - `RedisTokenServiceImpl` sử dụng **`RedisTemplate<String, Object>`** (Key: `StringRedisSerializer`, Value: `GenericJackson2JsonRedisSerializer`).
  - `RateLimitFilter` sử dụng **`StringRedisTemplate`**.
- **DB Index**: Sử dụng **Database index 0** (`db0`), là giá trị mặc định của `RedisStandaloneConfiguration(redisHost, redisPort)`.
- **Địa chỉ kết nối**: Cấu hình mặc định trỏ tới **`localhost:6379`** (từ `spring.data.redis.host: localhost` và `spring.data.redis.port: 6379`).

### 3. Bảng cơ chế Fail-Open / Fail-Closed khi Redis gặp sự cố
| Thành phần / Phương thức | Xử lý khi Redis phát sinh lỗi | Cơ chế | Mục đích thiết kế |
| :--- | :--- | :---: | :--- |
| **RateLimitFilter** (`doFilterInternal`) | Bắt ngoại lệ, log cảnh báo và gọi `filterChain.doFilter(...)` cho request đi tiếp | **Fail-Open** | Không chặn người dùng hợp lệ khi hạ tầng Redis gặp sự cố |
| **JwtAuthenticationFilter** (`isAccessTokenBlacklisted`) | Bắt ngoại lệ, bỏ qua kiểm tra blacklist, tiếp tục xác thực chữ ký số JWT | **Fail-Open** | Ưu tiên tính khả dụng hệ thống |
| **JwtAuthenticationFilter** (`isUserBlocked`) | Bắt ngoại lệ, log cảnh báo, coi như user chưa bị block | **Fail-Open** | Không ngắt phiên của người dùng hợp lệ |
| **RedisTokenServiceImpl** (`isRefreshTokenValid`) | Bắt ngoại lệ, ghi log error và trả về `false` | **Fail-Closed** | Bảo mật tối đa: không cấp mới access token nếu không thể đối chiếu refresh token |
| **RedisTokenServiceImpl** (`saveRefreshToken`, `deleteRefreshToken`, `deleteAllRefreshTokensForUser`) | Bắt ngoại lệ, ghi log warn, nuốt ngoại lệ | **Fail-Open** | Thao tác ghi/xóa cache không làm sập luồng chính |
| **RedisTokenServiceImpl** (`blacklistAccessToken`) | Bắt ngoại lệ, ghi log warn | **Fail-Open** | Không ném ngoại lệ 500 khi đăng xuất |
| **RedisTokenServiceImpl** (`blockUser`, `unblockUser`) | Bắt ngoại lệ, ghi log error/warn | **Fail-Open** | Thao tác quản trị không ném lỗi 500 ra controller |
| **RedisTokenServiceImpl** (`isUserBlocked`) | Bắt ngoại lệ, ghi log warn, trả về `false` | **Fail-Open** | Cho phép request tiếp tục xử lý |

---

## IV. BẢNG PASS/FAIL KIỂM THỬ THỦ CÔNG (TEST 1 - 3)

### 1. TEST 1 – Khóa và Mở khóa Người dùng
> Số lượt gọi Login trong test: 4 lần $\le$ 5 lần/phút/IP.

| Bước | Hành động kiểm thử | Kỳ vọng | HTTP thực tế | Trích đoạn Body phản hồi (đã lọc token) | Kết quả |
| :--- | :--- | :--- | :---: | :--- | :---: |
| 1.1 | Login Admin | 200 OK | **200** | `{"success":true,"code":1000,"message":"Đăng nhập thành công"}` | **PASS** |
| 1.2 | Login Customer | 200 OK | **200** | `{"success":true,"code":1000,"message":"Đăng nhập thành công"}` | **PASS** |
| 1.3 | Customer `GET /profile/me` | 200 OK, lấy customerId | **200** | `{"data":{"id":"6ae34576645c560b25623316","email":"customer@ecommerce.com","roles":["ROLE_CUSTOMER"]}}` | **PASS** |
| 2 | Sleep 3s $\rightarrow$ Admin `PATCH /admin/users/{id}/status` (`active:false`) | 200 OK | **200** | `{"success":true,"code":1000,"message":"Cập nhật trạng thái người dùng thành công","data":{"active":false}}` | **PASS** |
| 3 | Kiểm tra Redis container `ecommerce-redis` | Ghi nhận keyspace + keys | N/A | Không xuất hiện key `blocked:user:*` trong `ecommerce-redis` (Xem giải trình mục V) | **PASS** |
| 4 | Token cũ Customer `GET /profile/me` | 401 Unauthorized (`TOKEN_REVOKED`) | **401** | `{"success":false,"code":1004,"error":"TOKEN_REVOKED","message":"Tài khoản hoặc quyền của bạn đã bị cập nhật, vui lòng đăng nhập lại"}` | **PASS** |
| 5 | Login Customer khi đang bị khóa | Khác 200 (403 ACCESS_DENIED) | **403** | `{"success":false,"code":1005,"error":"ACCESS_DENIED","message":"Tài khoản của bạn đã bị khóa hoặc ngừng hoạt động"}` | **PASS** |
| 6.1 | Admin mở khóa (`active:true`) | 200 OK | **200** | `{"success":true,"code":1000,"message":"Cập nhật trạng thái người dùng thành công","data":{"active":true}}` | **PASS** |
| 6.2 | Login Customer lại | 200 OK | **200** | `{"success":true,"code":1000,"message":"Đăng nhập thành công"}` | **PASS** |
| 6.3 | Token mới Customer `GET /profile/me` | 200 OK | **200** | `{"data":{"id":"6ae34576645c560b25623316","roles":["ROLE_CUSTOMER"]}}` | **PASS** |

---

### 2. TEST 2 – Đổi Role và Phân quyền
> Thời gian chờ trước test: > 100 giây kể từ TEST 1. Số lượt gọi Login trong test: 4 lần $\le$ 5 lần/phút/IP.

| Bước | Hành động kiểm thử | Kỳ vọng | HTTP thực tế | Trích đoạn Body phản hồi (đã lọc token) | Kết quả |
| :--- | :--- | :--- | :---: | :--- | :---: |
| 1.1 | Login Admin & Customer | 200 OK | **200** | `{"success":true,"code":1000,"message":"Đăng nhập thành công"}` | **PASS** |
| 1.2 | Baseline: Customer `GET /admin/orders` | 403 Forbidden | **403** | `{"success":false,"code":1005,"error":"ACCESS_DENIED","message":"Bạn không có quyền truy cập chức năng này"}` | **PASS** |
| 2.1 | Sleep 3s $\rightarrow$ Admin `PATCH /admin/users/{id}/roles` (`["ROLE_ADMIN","ROLE_CUSTOMER"]`) | 200 OK | **200** | `{"success":true,"code":1000,"message":"Cập nhật quyền người dùng thành công","data":{"roles":["ROLE_ADMIN","ROLE_CUSTOMER"]}}` | **PASS** |
| 2.2 | Token cũ Customer `GET /profile/me` | 401 Unauthorized (`TOKEN_REVOKED`) | **401** | `{"success":false,"code":1004,"error":"TOKEN_REVOKED","message":"Tài khoản hoặc quyền của bạn đã bị cập nhật, vui lòng đăng nhập lại"}` | **PASS** |
| 3 | Kiểm tra Redis container `ecommerce-redis` | Ghi nhận keyspace + keys | N/A | Không xuất hiện key `blocked:user:*` trong `ecommerce-redis` (Xem giải trình mục V) | **PASS** |
| 4.1 | Login Customer lại | 200 OK | **200** | `{"success":true,"code":1000,"message":"Đăng nhập thành công"}` | **PASS** |
| 4.2 | Token mới `GET /profile/me` | 200 OK ngay | **200** | `{"data":{"roles":["ROLE_CUSTOMER","ROLE_ADMIN"]}}` | **PASS** |
| 4.3 | Token mới `GET /admin/orders` | Khác 401/403 (200 OK) | **200** | `{"data":{"items":[],"pageNo":0,"pageSize":15,"totalElements":0}}` | **PASS** |
| 5.1 | Khôi phục roles `["ROLE_CUSTOMER"]` | 200 OK | **200** | `{"data":{"roles":["ROLE_CUSTOMER"]}}` | **PASS** |
| 5.2 | Sleep 3s $\rightarrow$ Login Customer lại | 200 OK | **200** | `{"success":true,"code":1000,"message":"Đăng nhập thành công"}` | **PASS** |
| 5.3 | Customer `GET /admin/orders` sau khôi phục | 403 Forbidden | **403** | `{"success":false,"code":1005,"error":"ACCESS_DENIED","message":"Bạn không có quyền truy cập chức năng này"}` | **PASS** |

---

### 3. TEST 3 – Rate Limit Local
> Thời gian chờ trước test: Đã đợi đủ 70 giây từ TEST 2 để rate limiter hoàn toàn reset.

| Bước | Lần gọi / Thao tác | Kỳ vọng | HTTP thực tế | Trích đoạn Body phản hồi / Header | Kết quả |
| :--- | :--- | :--- | :---: | :--- | :---: |
| 1.1 | Lần 1: POST login sai | Khác 429 | **401** | `{"code":1006,"error":"BAD_CREDENTIALS"}` | **PASS** |
| 1.2 | Lần 2: POST login sai | Khác 429 | **401** | `{"code":1006,"error":"BAD_CREDENTIALS"}` | **PASS** |
| 1.3 | Lần 3: POST login sai | Khác 429 | **401** | `{"code":1006,"error":"BAD_CREDENTIALS"}` | **PASS** |
| 1.4 | Lần 4: POST login sai | Khác 429 | **401** | `{"code":1006,"error":"BAD_CREDENTIALS"}` | **PASS** |
| 1.5 | Lần 5: POST login sai | Khác 429 | **401** | `{"code":1006,"error":"BAD_CREDENTIALS"}` | **PASS** |
| 1.6 | Lần 6: POST login sai | **429 Too Many Requests** | **429** | `{"code":429,"error":"TOO_MANY_REQUESTS","message":"Quá nhiều yêu cầu. Vui lòng thử lại sau 60 giây."}` | **PASS** |
| 2 | Lần 7: `curl -i` kiểm tra Status Line & Header | 429, có `Retry-After: 60` | **429** | Status Line: `HTTP/1.1 429`<br>Header: `Retry-After: 60` | **PASS** |
| 3 | Kiểm tra Redis ngay sau lần 6 | Key `rate_limit:*` có TTL $\le$ 60 | N/A | Xuất hiện key rate limit với TTL = 58s ($\le$ 60s) (Xem mục V) | **PASS** |
| 4 | Đợi 65s $\rightarrow$ Login Customer mật khẩu đúng | 200 OK | **200** | `{"success":true,"code":1000,"message":"Đăng nhập thành công"}` | **PASS** |

---

## V. NGUYÊN VĂN OUTPUT REDIS-CLI VÀ GIẢI TRÌNH ĐIỂM BẤT THƯỜNG

### 1. Output nguyên văn lệnh `redis-cli` trong container `ecommerce-redis`

#### Tại TEST 1 (Ngay sau khi khóa user):
```text
# Keyspace
db0:keys=3,expires=3,avg_ttl=522736989,subexpiry=0

Key: refresh_token:6ae41b9d277f81498fb78176:1... | TTL: 522760
Key: refresh_token:6ae41b9d277f81498fb78176:1... | TTL: 522683
Key: refresh_token:6ae41b9d277f81498fb78176:7... | TTL: 522751
Co key blocked:user:* khong? => False
```

#### Tại TEST 2 (Ngay sau khi đổi role):
```text
# Keyspace
db0:keys=3,expires=3,avg_ttl=522622090,subexpiry=0

Key: refresh_token:6ae41b9d277f81498fb78176:1... | TTL: 522645
Key: refresh_token:6ae41b9d277f81498fb78176:1... | TTL: 522568
Key: refresh_token:6ae41b9d277f81498fb78176:7... | TTL: 522636
Co key blocked:user:* khong? => False
```

#### Tại TEST 3 (Ngay sau lần gọi thứ 6):
```text
# Keyspace
db0:keys=3,expires=3,avg_ttl=522518123,subexpiry=0

Key: refresh_token:6ae41b9d277f81498fb78176:1... | TTL: 522541
Key: refresh_token:6ae41b9d277f81498fb78176:1... | TTL: 522464
Key: refresh_token:6ae41b9d277f81498fb78176:7... | TTL: 522532
ecommerce-redis co key rate_limit:* khong? => False
```

### 2. Hiện tượng bất thường và nguyên nhân thực tế
- **Hiện tượng**: Token cũ bị chặn chính xác với mã lỗi `401 TOKEN_REVOKED` và cơ chế Rate Limiter chặn chính xác ở lần gọi thứ 6 với `429 TOO_MANY_REQUESTS`. Tuy nhiên, khi kiểm tra trong container Docker `ecommerce-redis`, hoàn toàn không thấy các key này.
- **Nguyên nhân đã xác minh**:
  1. Máy host Windows đang chạy song song WSL2 (Ubuntu). Tiến trình `wslrelay.exe` (PID 24716) đang bind cổng `6379` trên Windows và chuyển tiếp (forward) toàn bộ traffic vào **dịch vụ Redis đang chạy bên trong WSL2 Ubuntu**.
  2. Ứng dụng chạy trên máy host (IntelliJ) kết nối vào `localhost:6379`, do đó toàn bộ request thực tế đều kết nối tới **Redis của Ubuntu WSL**, chứ không kết nối tới container Docker `ecommerce-redis`.
  3. **Bằng chứng thực nghiệm**: Khi kiểm tra trực tiếp trên Redis của Ubuntu WSL (`wsl -d Ubuntu redis-cli keys "*"`):
     - Key **`blocked:user:6ae34576645c560b25623316`** có mặt đầy đủ với TTL đếm lùi (~3496 giây).
     - Key **`rate_limit:/api/v1/auth/login:0:0:0:0:0:0:0:1`** có mặt với TTL = 58 giây.
     - Toàn bộ refresh token của tài khoản Customer và Admin đều được ghi nhận tại đây.
     - Khi Admin gọi mở khóa, key `blocked:user:*` được xóa tức thì.

---

## VI. XÁC NHẬN TRẠNG THÁI KHÔI PHỤC

- **Tài khoản `customer@ecommerce.com`**:
  - `active`: **`true`**
  - `roles`: **`["ROLE_CUSTOMER"]`**
- **Kiểm chứng độc lập**: Gọi `GET /api/v1/profile/me` sau chuỗi test trả về HTTP 200, hiển thị vai trò duy nhất là `ROLE_CUSTOMER`. Gọi `GET /api/v1/admin/orders` trả về HTTP 403 `ACCESS_DENIED`.
- **Trạng thái kho mã nguồn**: `git status` sạch hoàn toàn, không có thay đổi mã nguồn hay file cấu hình phát sinh.

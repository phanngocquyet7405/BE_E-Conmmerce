# Kiến Trúc & Boilerplate E-Commerce Backend (Spring Boot 3.3 + MongoDB Atlas + Redis)

Tài liệu này cung cấp định hướng kiến trúc và bộ mã nguồn nền tảng (boilerplate) hoàn chỉnh cho đồ án **E-Commerce Backend** với mô hình **Single-Seller / Cửa hàng độc lập**, sử dụng **Java 21**, **Spring Boot 3.3.x**, **MongoDB Atlas (Spring Data MongoDB)**, và **Redis (Quản lý Refresh Token & Blacklist Logout)**, tuân thủ nghiêm ngặt mô hình **3-Tier MVC** (`Controller` - `Service` - `Repository`).

> **Lưu ý về Hibernate & MongoDB**: Hibernate vốn là ORM chuyên biệt cho các cơ sở dữ liệu quan hệ (RDBMS). Trong thế giới NoSQL/Document của MongoDB, **Spring Data MongoDB** chính là công nghệ chuẩn mực nhất của hệ sinh thái Spring. Nó cung cấp đầy đủ các annotation ánh xạ (`@Document`, `@Field`, `@Indexed`), tự động quản lý kết nối Atlas Connection String (`mongodb+srv://`), hỗ trợ kiểm toán dữ liệu (`@CreatedDate`, `@LastModifiedDate`), và triển khai toàn vẹn mô hình 3 lớp **Entity/Document - Repository - Service - Controller**.

---

## 1. Định Hướng Kiến Trúc & Luồng Xử Lý (3-Tier MVC + Redis)

```text
HTTP Request ──> ApiRoutes ──> JwtAuthenticationFilter ──> Controller (@PreAuthorize)
                                      │                        │
                                 Kiểm tra Redis          Validate DTO (@Valid)
                               (Token Blacklist)               │
                                                               ▼
                                                      Service (Interface & Impl)
                                                      ┌────────┴────────┐
                                                      ▼                 ▼
                                              Redis Service      MapStruct Mapper
                                            (Refresh Token /            │
                                             Blacklist Cache)           ▼
                                                                MongoRepository
                                                                        │
                                                                        ▼
                                                                  MongoDB Atlas
```

### 1.1. Luồng xử lý phân tầng
1. **Presentation Layer (`controller`, `dto.request`, `dto.response`)**:
   - Sử dụng tập trung các hằng số định tuyến trong `ApiRoutes`.
   - Kiểm tra dữ liệu đầu vào thông qua Bean Validation (`@Valid`, `@NotBlank`, `@Min`).
   - Phân quyền theo chuẩn **Single-Seller**: Khách mua hàng (`ROLE_CUSTOMER`) và Chủ cửa hàng (`ROLE_ADMIN`).
   - Đóng gói dữ liệu đầu ra qua `ApiResponse<T>` và `PageResponse<T>` chuẩn hóa.
2. **Business Logic Layer (`service`, `service.impl`, `mapper`, `exception`)**:
   - Chịu trách nhiệm thực thi logic nghiệp vụ (quản lý tồn kho sản phẩm, giỏ hàng, xác thực tài khoản).
   - Tương tác với **Redis** để lưu trữ Refresh Token (Whitelist) và đánh dấu Access Token đã thu hồi (Blacklist) khi người dùng bấm Đăng xuất (Logout).
   - Sử dụng **MapStruct** để map qua lại giữa `Document` và `DTO` lúc biên dịch.
   - Ném lỗi thông qua `AppException` kèm `ErrorCode` chi tiết.
3. **Data Access Layer (`repository`, `document`)**:
   - Giao tiếp với cụm **MongoDB Atlas** thông qua `MongoRepository<T, ID>`.
   - Định nghĩa Schema linh hoạt bằng Document annotations (`@Document`, `@Indexed`, `@Id`).

---

## 2. Cấu Trúc Thư Mục Dự Án (Directory Structure)

```text
ecommerce-backend/
├── pom.xml
├── .env.example
└── src/main/
    ├── java/com/ecommerce/
    │   ├── EcommerceApplication.java
    │   ├── common/
    │   │   ├── constant/
    │   │   │   ├── ApiRoutes.java                 # Quản lý tập trung toàn bộ Router & Versioning
    │   │   │   └── RoleEnum.java                  # ROLE_ADMIN (Chủ shop) & ROLE_CUSTOMER (Khách mua)
    │   │   └── response/
    │   │       ├── ApiResponse.java               # Cấu trúc JSON trả về chuẩn
    │   │       └── PageResponse.java              # Cấu trúc JSON phân trang
    │   ├── config/
    │   │   ├── AppProperties.java                 # Đọc cấu hình JWT, Redis, MongoDB Type-Safe
    │   │   ├── MongoConfig.java                   # Kích hoạt Auditing (@CreatedDate, @LastModifiedDate)
    │   │   ├── RedisConfig.java                   # Cấu hình RedisTemplate chuẩn JSON serializer
    │   │   ├── OpenApiConfig.java                 # Swagger UI với Bearer JWT
    │   │   └── SecurityConfig.java                # Phân quyền Router & Stateless SecurityFilterChain
    │   ├── exception/
    │   │   ├── ErrorCode.java                     # Bảng mã lỗi phân tầng (1xxx, 2xxx, 3xxx, 9xxx)
    │   │   ├── AppException.java                  # Custom RuntimeException
    │   │   ├── ErrorResponse.java                 # JSON chi tiết khi gặp sự cố
    │   │   └── GlobalExceptionHandler.java        # Xử lý ngoại lệ toàn cục (@RestControllerAdvice)
    │   ├── security/
    │   │   ├── CustomUserDetails.java             # User model nạp vào SecurityContext
    │   │   ├── CustomUserDetailsService.java      # Truy vấn User từ MongoDB
    │   │   ├── JwtAuthenticationEntryPoint.java   # Xử lý lỗi 401 Unauthorized
    │   │   ├── CustomAccessDeniedHandler.java     # Xử lý lỗi 403 Forbidden
    │   │   └── JwtAuthenticationFilter.java       # Bộ lọc kiểm tra JWT & check Blacklist Redis
    │   ├── util/
    │   │   └── JwtUtil.java                       # Tiện ích tạo/giải mã Access & Refresh Token (JJWT 0.12.6)
    │   ├── document/
    │   │   ├── BaseDocument.java                  # Chứa id, createdAt, updatedAt
    │   │   ├── User.java                          # Document tài khoản khách & admin
    │   │   ├── Category.java                      # Document danh mục hàng hóa
    │   │   └── Product.java                       # Document sản phẩm
    │   ├── dto/
    │   │   ├── request/
    │   │   │   ├── LoginRequest.java              # DTO đăng nhập
    │   │   │   ├── RegisterRequest.java           # DTO đăng ký khách hàng
    │   │   │   ├── RefreshTokenRequest.java       # DTO yêu cầu cấp lại Access Token
    │   │   │   └── ProductRequest.java            # DTO thêm/sửa sản phẩm
    │   │   └── response/
    │   │       ├── TokenResponse.java             # Cặp Access/Refresh Token trả về
    │   │       └── ProductResponse.java           # DTO sản phẩm gửi về Client
    │   ├── mapper/
    │   │   └── ProductMapper.java                 # MapStruct chuyển đổi Product Document <-> DTO
    │   ├── repository/
    │   │   ├── UserRepository.java                # MongoRepository cho users
    │   │   ├── CategoryRepository.java            # MongoRepository cho categories
    │   │   └── ProductRepository.java             # MongoRepository cho products
    │   ├── service/
    │   │   ├── RedisTokenService.java             # Quản lý Whitelist Refresh Token & Blacklist Access Token
    │   │   ├── AuthService.java                   # Đăng nhập, Đăng ký, Refresh Token, Đăng xuất
    │   │   ├── ProductService.java                # Nghiệp vụ quản lý sản phẩm
    │   │   └── impl/
    │   │       ├── RedisTokenServiceImpl.java     # Triển khai thao tác Redis TTL
    │   │       ├── AuthServiceImpl.java           # Triển khai xác thực & đồng bộ Redis
    │   │       └── ProductServiceImpl.java        # Triển khai CRUD sản phẩm
    │   └── controller/
    │       ├── AuthController.java                # Router /api/v1/auth/**
    │       └── ProductController.java             # Router Public & Admin cho sản phẩm
    └── resources/
        ├── application.yml                        # Cấu hình chung
        ├── application-dev.yml                    # Môi trường Dev (kèm link MongoDB Atlas mẫu)
        └── application-prod.yml                   # Môi trường Production
```

---

## 3. Cấu Hình Dependencies (`pom.xml`)

File `pom.xml` bao gồm **Spring Data MongoDB**, **Spring Data Redis (Lettuce)**, **Spring Security**, **JJWT 0.12.6**, **Lombok** và **MapStruct**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.4</version>
        <relativePath/>
    </parent>

    <groupId>com.ecommerce</groupId>
    <artifactId>ecommerce-backend</artifactId>
    <version>1.0.0</version>
    <name>ecommerce-backend</name>
    <description>Spring Boot 3 E-Commerce Backend with MongoDB Atlas, Redis, and 3-Tier MVC Architecture</description>

    <properties>
        <java.version>21</java.version>
        <jjwt.version>0.12.6</jjwt.version>
        <mapstruct.version>1.5.5.Final</mapstruct.version>
        <lombok-mapstruct-binding.version>0.2.0</lombok-mapstruct-binding.version>
        <springdoc.version>2.6.0</springdoc.version>
    </properties>

    <dependencies>
        <!-- 1. Spring Boot Web & Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- 2. Spring Data MongoDB (Hỗ trợ MongoDB Atlas) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-mongodb</artifactId>
        </dependency>

        <!-- 3. Spring Data Redis (Quản lý Token Cache & Blacklist) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- 4. Spring Security & JJWT 0.12.6 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- 5. Lombok & MapStruct -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct</artifactId>
            <version>${mapstruct.version}</version>
        </dependency>

        <!-- 6. OpenAPI / Swagger UI -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>

        <!-- 7. Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok-mapstruct-binding</artifactId>
                            <version>${lombok-mapstruct-binding.version}</version>
                        </path>
                        <path>
                            <groupId>org.mapstruct</groupId>
                            <artifactId>mapstruct-processor</artifactId>
                            <version>${mapstruct.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 4. Cấu Hình Biến Môi Trường, MongoDB Atlas & Redis

### 4.1. File `.env.example`
Dưới đây là link kết nối mẫu chuẩn `mongodb+srv://` tới MongoDB Atlas và kết nối Redis (hỗ trợ cả Redis Local hoặc Redis Cloud như Upstash/Aiven):

```ini
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080

# Link MongoDB Atlas mẫu (Thay chuỗi cluster của bạn khi có link chính xác)
# Cú pháp: mongodb+srv://<username>:<password>@<cluster>.mongodb.net/<database_name>?retryWrites=true&w=majority
MONGODB_URI=mongodb+srv://student_dev:DemoPassword123@cluster0.abcde.mongodb.net/ecommerce_db?retryWrites=true&w=majority

# Cấu hình Redis (Local hoặc Upstash Cloud)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT Secrets (Chuỗi Base64 >= 256-bit)
JWT_ACCESS_SECRET=bXktc3VwZXItc2VjcmV0LWaccessLtleS1mb3ItZWNvbW1lcmNlLTIwMjYtYmFja2VuZA==
JWT_REFRESH_SECRET=bXktc3VwZXItc2VjcmV0LXJlZnJlc2gta2V5LWZvci1lY29tbWVyY2UtMjAyNi1iYWNrZW5k
JWT_ACCESS_EXPIRATION_MS=3600000
JWT_REFRESH_EXPIRATION_MS=604800000

CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
```

### 4.2. File `src/main/resources/application.yml`
```yaml
spring:
  application:
    name: ecommerce-backend
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

server:
  port: ${SERVER_PORT:8080}
  servlet:
    context-path: /

app:
  jwt:
    issuer: ecommerce-single-store
    access-secret: ${JWT_ACCESS_SECRET:bXktc3VwZXItc2VjcmV0LWaccessLtleS1mb3ItZWNvbW1lcmNlLTIwMjYtYmFja2VuZA==}
    refresh-secret: ${JWT_REFRESH_SECRET:bXktc3VwZXItc2VjcmV0LXJlZnJlc2gta2V5LWZvci1lY29tbWVyY2UtMjAyNi1iYWNrZW5k}
    access-expiration-ms: ${JWT_ACCESS_EXPIRATION_MS:3600000}       # 1 Giờ
    refresh-expiration-ms: ${JWT_REFRESH_EXPIRATION_MS:604800000}   # 7 Ngày
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173}
```

### 4.3. File `src/main/resources/application-dev.yml`
```yaml
spring:
  data:
    mongodb:
      # Link Atlas tạm thời, tự động fall-back về localhost nếu chưa cung cấp
      uri: ${MONGODB_URI:mongodb://localhost:27017/ecommerce_dev_db}
      auto-index-creation: true

    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 5000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 2

logging:
  level:
    com.ecommerce: DEBUG
    org.springframework.data.mongodb: INFO
    org.springframework.security: INFO
```

### 4.4. Cấu hình Redis & MongoDB Auditing
```java
package com.ecommerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key dùng StringSerializer, Value dùng Jackson2JsonSerializer
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
```

```java
package com.ecommerce.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing // Tự động cập nhật @CreatedDate và @LastModifiedDate cho Document
public class MongoConfig {
}
```

---

## 5. Quản Lý Router Tập Trung & Phân Quyền Single-Seller

Vì là mô hình **1 cửa hàng bán trực tiếp**, hệ thống chỉ cần 2 vai trò cốt lõi:
- `ROLE_ADMIN`: Chủ shop / Quản trị viên (quản lý kho, đăng sản phẩm, xem đơn hàng).
- `ROLE_CUSTOMER`: Người mua hàng (xem sản phẩm, thêm giỏ hàng, đặt hàng, xem lịch sử mua).

### 5.1. Quản lý Router (`ApiRoutes.java` & `RoleEnum.java`)
```java
package com.ecommerce.common.constant;

public final class ApiRoutes {
    private ApiRoutes() {}

    public static final String API_V1 = "/api/v1";

    public static final class Auth {
        private Auth() {}
        public static final String BASE = API_V1 + "/auth";
        public static final String LOGIN = "/login";
        public static final String REGISTER = "/register";
        public static final String REFRESH = "/refresh-token";
        public static final String LOGOUT = "/logout";
        public static final String ME = "/me";
    }

    public static final class Products {
        private Products() {}
        public static final String PUBLIC_BASE = API_V1 + "/public/products";
        public static final String ADMIN_BASE = API_V1 + "/admin/products";
        public static final String BY_ID = "/{id}";
    }

    public static final class Categories {
        private Categories() {}
        public static final String PUBLIC_BASE = API_V1 + "/public/categories";
        public static final String ADMIN_BASE = API_V1 + "/admin/categories";
    }

    public static final class Orders {
        private Orders() {}
        public static final String CUSTOMER_BASE = API_V1 + "/orders";
        public static final String ADMIN_BASE = API_V1 + "/admin/orders";
    }
}
```

```java
package com.ecommerce.common.constant;

public enum RoleEnum {
    ROLE_CUSTOMER,
    ROLE_ADMIN
}
```

---

## 6. Phân Loại Mã Lỗi Toàn Cục (`exception`)

```java
package com.ecommerce.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 1xxx: Auth, Token & Redis
    UNAUTHENTICATED(1001, "Bạn chưa đăng nhập hoặc phiên làm việc không hợp lệ", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(1002, "Token đã hết hạn, vui lòng làm mới phiên đăng nhập", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(1003, "Chữ ký hoặc cấu trúc Token không hợp lệ", HttpStatus.UNAUTHORIZED),
    TOKEN_REVOKED(1004, "Token này đã bị thu hồi hoặc đã đăng xuất", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(1005, "Bạn không có quyền truy cập chức năng dành cho Quản trị viên", HttpStatus.FORBIDDEN),
    BAD_CREDENTIALS(1006, "Email hoặc mật khẩu không chính xác", HttpStatus.UNAUTHORIZED),
    EMAIL_ALREADY_EXISTS(1007, "Email này đã được đăng ký tài khoản", HttpStatus.CONFLICT),

    // 2xxx: Validation & Request Format
    VALIDATION_ERROR(2001, "Dữ liệu gửi lên không thỏa mãn yêu cầu", HttpStatus.BAD_REQUEST),
    MALFORMED_JSON(2002, "Định dạng JSON gửi lên bị sai cú pháp", HttpStatus.BAD_REQUEST),

    // 3xxx: E-Commerce Product & Category
    PRODUCT_NOT_FOUND(3001, "Không tìm thấy sản phẩm yêu cầu", HttpStatus.NOT_FOUND),
    PRODUCT_SKU_EXISTS(3002, "Mã SKU sản phẩm đã tồn tại", HttpStatus.CONFLICT),
    CATEGORY_NOT_FOUND(3003, "Không tìm thấy danh mục sản phẩm", HttpStatus.NOT_FOUND),
    INSUFFICIENT_STOCK(3004, "Số lượng trong kho không đủ", HttpStatus.BAD_REQUEST),

    // 9xxx: System Error
    INTERNAL_SERVER_ERROR(9999, "Lỗi hệ thống máy chủ, vui lòng thử lại sau", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
}
```

---

## 7. Quản Lý Token Bằng Redis & JWT Tiện Ích (`util` & `service`)

Hệ thống kết hợp **Stateless JWT** và **Redis State**:
- **Whitelist Refresh Token trong Redis**: `refresh_token:{userId}:{tokenId}` kèm thời gian sống (TTL = 7 ngày). Khi làm mới hoặc đăng xuất, xóa token này để hủy phiên.
- **Blacklist Access Token trong Redis**: `blacklist_token:{accessToken}` với TTL bằng thời gian còn lại của token. Khi đăng xuất, đưa token vào Blacklist để vô hiệu hóa ngay lập tức.

### 7.1. Service thao tác Redis Token (`RedisTokenService.java` & `impl`)
```java
package com.ecommerce.service;

import java.util.concurrent.TimeUnit;

public interface RedisTokenService {
    void saveRefreshToken(String userId, String tokenId, long ttlMillis);
    boolean isRefreshTokenValid(String userId, String tokenId);
    void deleteRefreshToken(String userId, String tokenId);
    void blacklistAccessToken(String accessToken, long remainingTtlMillis);
    boolean isAccessTokenBlacklisted(String accessToken);
}
```

```java
package com.ecommerce.service.impl;

import com.ecommerce.service.RedisTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisTokenServiceImpl implements RedisTokenService {

    private static final String REFRESH_KEY_PREFIX = "refresh_token:";
    private static final String BLACKLIST_KEY_PREFIX = "blacklist_token:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void saveRefreshToken(String userId, String tokenId, long ttlMillis) {
        String key = REFRESH_KEY_PREFIX + userId + ":" + tokenId;
        redisTemplate.opsForValue().set(key, "ACTIVE", ttlMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isRefreshTokenValid(String userId, String tokenId) {
        String key = REFRESH_KEY_PREFIX + userId + ":" + tokenId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public void deleteRefreshToken(String userId, String tokenId) {
        String key = REFRESH_KEY_PREFIX + userId + ":" + tokenId;
        redisTemplate.delete(key);
    }

    @Override
    public void blacklistAccessToken(String accessToken, long remainingTtlMillis) {
        if (remainingTtlMillis > 0) {
            String key = BLACKLIST_KEY_PREFIX + accessToken;
            redisTemplate.opsForValue().set(key, "REVOKED", remainingTtlMillis, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public boolean isAccessTokenBlacklisted(String accessToken) {
        String key = BLACKLIST_KEY_PREFIX + accessToken;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
```

### 7.2. Lớp tiện ích `JwtUtil.java`
```java
package com.ecommerce.util;

import com.ecommerce.config.AppProperties;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.security.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TOKEN_ID = "jti";
    public static final String CLAIM_TOKEN_TYPE = "typ";
    public static final String TYPE_ACCESS = "ACCESS";
    public static final String TYPE_REFRESH = "REFRESH";

    private final AppProperties appProperties;
    private SecretKey accessKey;
    private SecretKey refreshKey;

    @PostConstruct
    public void initKeys() {
        this.accessKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(appProperties.getJwt().getAccessSecret()));
        this.refreshKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(appProperties.getJwt().getRefreshSecret()));
    }

    public String generateAccessToken(CustomUserDetails userDetails) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(appProperties.getJwt().getAccessExpirationMs());

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(appProperties.getJwt().getIssuer())
                .subject(userDetails.getUsername())
                .claim(CLAIM_USER_ID, userDetails.getId())
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(accessKey, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(CustomUserDetails userDetails, String tokenId) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(appProperties.getJwt().getRefreshExpirationMs());

        return Jwts.builder()
                .id(tokenId)
                .issuer(appProperties.getJwt().getIssuer())
                .subject(userDetails.getUsername())
                .claim(CLAIM_USER_ID, userDetails.getId())
                .claim(CLAIM_TOKEN_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(refreshKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims verifyAccessToken(String token) {
        Claims claims = parseToken(token, accessKey);
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            throw new AppException(ErrorCode.INVALID_TOKEN, "Token không phải là Access Token");
        }
        return claims;
    }

    public Claims verifyRefreshToken(String token) {
        Claims claims = parseToken(token, refreshKey);
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            throw new AppException(ErrorCode.INVALID_TOKEN, "Token không phải là Refresh Token");
        }
        return claims;
    }

    private Claims parseToken(String token, SecretKey secretKey) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(appProperties.getJwt().getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }
}
```

### 7.3. Bộ lọc kiểm tra JWT và Redis Blacklist (`JwtAuthenticationFilter.java`)
```java
package com.ecommerce.security;

import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.service.RedisTokenService;
import com.ecommerce.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ATTR_AUTH_ERROR_CODE = "AUTH_ERROR_CODE";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final RedisTokenService redisTokenService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(BEARER_PREFIX.length());
        try {
            // 1. Kiểm tra xem Token có nằm trong Blacklist của Redis (đã logout) hay không
            if (redisTokenService.isAccessTokenBlacklisted(jwt)) {
                throw new AppException(ErrorCode.TOKEN_REVOKED);
            }

            // 2. Xác minh tính hợp lệ và giải mã Claims
            Claims claims = jwtUtil.verifyAccessToken(jwt);
            String email = claims.getSubject();

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (AppException ex) {
            request.setAttribute(ATTR_AUTH_ERROR_CODE, ex.getErrorCode());
        }

        filterChain.doFilter(request, response);
    }
}
```

---

## 8. Mô Hình 3 Lớp với MongoDB Atlas (Document - Repository - Service - Controller)

### 8.1. Tầng Document (`Product.java`)
```java
package com.ecommerce.document;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    private String id;

    @Indexed(unique = true)
    private String sku;

    @Field("name")
    private String name;

    @Indexed(unique = true)
    private String slug;

    private String description;

    @Field("price")
    private BigDecimal price;

    @Field("stock_quantity")
    private Integer stockQuantity;

    @Field("active")
    @Builder.Default
    private Boolean active = true;

    @Field("category_id")
    private String categoryId;

    @Field("category_name")
    private String categoryName;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;
}
```

### 8.2. Tầng Repository (`ProductRepository.java`)
```java
package com.ecommerce.repository;

import com.ecommerce.document.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {
    boolean existsBySkuIgnoreCase(String sku);
    Optional<Product> findBySlug(String slug);
    Page<Product> findAllByActiveTrue(Pageable pageable);
}
```

### 8.3. Tầng DTO & MapStruct (`ProductRequest.java`, `ProductResponse.java`, `ProductMapper.java`)
```java
package com.ecommerce.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductRequest {

    @NotBlank(message = "Mã SKU không được để trống")
    private String sku;

    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String name;

    @NotBlank(message = "Slug đường dẫn không được để trống")
    private String slug;

    private String description;

    @NotNull(message = "Giá không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá sản phẩm phải lớn hơn 0")
    private BigDecimal price;

    @NotNull(message = "Số lượng tồn kho không được để trống")
    @Min(value = 0, message = "Tồn kho không được âm")
    private Integer stockQuantity;

    @NotBlank(message = "Mã danh mục không được để trống")
    private String categoryId;
}
```

```java
package com.ecommerce.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class ProductResponse {
    private final String id;
    private final String sku;
    private final String name;
    private final String slug;
    private final String description;
    private final BigDecimal price;
    private final Integer stockQuantity;
    private final Boolean active;
    private final String categoryId;
    private final String categoryName;
    private final Instant createdAt;
}
```

```java
package com.ecommerce.mapper;

import com.ecommerce.document.Product;
import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.response.ProductResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductResponse toResponse(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Product toDocument(ProductRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateDocumentFromRequest(ProductRequest request, @MappingTarget Product product);
}
```

### 8.4. Tầng Service (`ProductService.java` & `ProductServiceImpl.java`)
```java
package com.ecommerce.service.impl;

import com.ecommerce.common.response.PageResponse;
import com.ecommerce.document.Category;
import com.ecommerce.document.Product;
import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Override
    public PageResponse<ProductResponse> getActiveProducts(Pageable pageable) {
        Page<ProductResponse> page = productRepository
                .findAllByActiveTrue(pageable)
                .map(productMapper::toResponse);
        return PageResponse.fromPage(page);
    }

    @Override
    public ProductResponse getProductById(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        return productMapper.toResponse(product);
    }

    @Override
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySkuIgnoreCase(request.getSku())) {
            throw new AppException(ErrorCode.PRODUCT_SKU_EXISTS);
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        Product product = productMapper.toDocument(request);
        product.setCategoryName(category.getName());

        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    public ProductResponse updateProduct(String id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getSku().equalsIgnoreCase(request.getSku())
                && productRepository.existsBySkuIgnoreCase(request.getSku())) {
            throw new AppException(ErrorCode.PRODUCT_SKU_EXISTS);
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        productMapper.updateDocumentFromRequest(request, product);
        product.setCategoryName(category.getName());

        return productMapper.toResponse(productRepository.save(product));
    }
}
```

### 8.5. Tầng Controller (`ProductController.java` & `AuthController.java`)
```java
package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.common.response.PageResponse;
import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // 1. PUBLIC ROUTER: Khách hàng xem danh sách sản phẩm
    @GetMapping(ApiRoutes.Products.PUBLIC_BASE)
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getActiveProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.ok(productService.getActiveProducts(pageable)));
    }

    // 2. PUBLIC ROUTER: Xem chi tiết sản phẩm
    @GetMapping(ApiRoutes.Products.PUBLIC_BASE + ApiRoutes.Products.BY_ID)
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getProductById(id)));
    }

    // 3. ADMIN ROUTER: Chủ cửa hàng tạo sản phẩm
    @PostMapping(ApiRoutes.Products.ADMIN_BASE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Tạo sản phẩm thành công", response));
    }

    // 4. ADMIN ROUTER: Chủ cửa hàng cập nhật sản phẩm
    @PutMapping(ApiRoutes.Products.ADMIN_BASE + ApiRoutes.Products.BY_ID)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật sản phẩm thành công", response));
    }
}
```

```java
package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.request.RefreshTokenRequest;
import com.ecommerce.dto.response.TokenResponse;
import com.ecommerce.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiRoutes.Auth.BASE)
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping(ApiRoutes.Auth.LOGIN)
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công", authService.login(request)));
    }

    @PostMapping(ApiRoutes.Auth.REFRESH)
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cấp lại Access Token thành công", authService.refreshToken(request)));
    }

    @PostMapping(ApiRoutes.Auth.LOGOUT)
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            authService.logout(accessToken);
        }
        return ResponseEntity.ok(ApiResponse.ok("Đăng xuất thành công", null));
    }
}
```

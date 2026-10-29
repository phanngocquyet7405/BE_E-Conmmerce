# E-Commerce Backend (Spring Boot 3 + MongoDB Atlas + Redis)

Dự án Backend Thương mại điện tử (Mô hình Cửa hàng độc lập / Single-Seller) tuân thủ kiến trúc 3 lớp MVC chuẩn mực:
- **Ngôn ngữ**: Java 21
- **Framework**: Spring Boot 3.3.4
- **Cơ sở dữ liệu**: MongoDB Atlas (Spring Data MongoDB)
- **Bộ nhớ đệm & Phiên**: Redis (Lettuce - Whitelist Refresh Token & Blacklist Logout)
- **Bảo mật**: Spring Security 6 + JJWT 0.12.6 (HMAC-SHA256)
- **Tài liệu API**: Swagger UI (OpenAPI 3.0)

## Hướng dẫn khởi chạy

### 1. Cấu hình biến môi trường
Tạo file `.env` từ `.env.example` hoặc cập nhật trực tiếp trong `src/main/resources/application-dev.yml`:
- `MONGODB_URI`: Link kết nối MongoDB Atlas của bạn.
- `REDIS_HOST`, `REDIS_PORT`: Thông số Redis.

### 2. Chạy ứng dụng
```bash
# Sử dụng Maven Wrapper
./mvnw clean spring-boot:run

# Hoặc nếu máy đã cài Maven:
mvn clean spring-boot:run
```

### 3. Kiểm tra Swagger UI
Truy cập: `http://localhost:8080/swagger-ui/index.html`

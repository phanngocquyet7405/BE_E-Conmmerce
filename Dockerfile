# ==========================================
# Giai đoạn 1: Build mã nguồn với Maven & JDK 21
# ==========================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Tận dụng layer cache của Docker
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy mã nguồn và đóng gói file JAR
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# Giai đoạn 2: Runtime image siêu nhẹ (JRE 21)
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Tạo thư mục lưu trữ ảnh tải lên (uploads)
RUN mkdir -p /app/uploads/images

# Copy file jar từ giai đoạn build
COPY --from=builder /build/target/ecommerce-backend-1.0.0.jar app.jar

# Cấu hình tối ưu JVM trong container (giới hạn 75% RAM để tránh bị Render Free 512MB OOM-Killed)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Mặc định lắng nghe cổng 8080 (Render tự gán biến $PORT khi chạy)
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# Stage 1: Build stage
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Cache maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and package application
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Production runtime stage
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Create upload directory for media fallback
RUN mkdir -p /app/uploads/images

# Copy compiled JAR
COPY --from=builder /build/target/ecommerce-backend-*.jar /app/app.jar

# Render dynamic port configuration and optimized JVM flags for 512MB RAM environment
ENV PORT=8080 \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0 -XX:MaxMetaspaceSize=110m -Xss512k -XX:+UseSerialGC -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

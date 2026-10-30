package com.ecommerce.security;

import com.ecommerce.common.constant.ApiRoutes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String RATE_LIMIT_PREFIX = "rate_limit:";
    private static final int DEFAULT_WINDOW_SECONDS = 60;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // Chỉ giới hạn các thao tác POST nhạy cảm
        if (!"POST".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        int maxRequests = resolveMaxRequests(uri);
        if (maxRequests <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        // Nhờ cấu hình server.forward-headers-strategy: native, request.getRemoteAddr() đã phản ánh đúng IP client
        String clientIp = request.getRemoteAddr();
        String bucketKey = RATE_LIMIT_PREFIX + uri + ":" + clientIp;

        try {
            Long count = stringRedisTemplate.opsForValue().increment(bucketKey);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(bucketKey, Duration.ofSeconds(DEFAULT_WINDOW_SECONDS));
            }

            if (count != null && count > maxRequests) {
                log.warn("Rate limit vượt ngưỡng cho IP [{}], URI [{}]: {}/{} requests", clientIp, uri, count, maxRequests);
                writeRateLimitResponse(response, uri);
                return;
            }
        } catch (Exception ex) {
            // Fail-open: Khi Redis lỗi/timeout, không làm gián đoạn người dùng hợp lệ
            log.warn("Redis Rate Limiter không khả dụng ({}). Bỏ qua rate limit và tiếp tục.", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private int resolveMaxRequests(String uri) {
        if (uri.equals(ApiRoutes.Auth.BASE + ApiRoutes.Auth.LOGIN)) {
            return 5; // Tối đa 5 lần thử đăng nhập / phút / IP chống brute-force
        }
        if (uri.equals(ApiRoutes.Auth.BASE + ApiRoutes.Auth.REGISTER)) {
            return 5; // Tối đa 5 lần đăng ký / phút / IP chống spam account
        }
        if (uri.equals(ApiRoutes.Auth.BASE + ApiRoutes.Auth.REFRESH)) {
            return 10; // Tối đa 10 lần refresh token / phút / IP
        }
        if (uri.startsWith(ApiRoutes.Media.BASE)) {
            return 10; // Tối đa 10 lần upload ảnh / phút / IP bảo vệ băng thông và Cloudinary quota
        }
        return -1;
    }

    private void writeRateLimitResponse(HttpServletResponse response, String uri) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(DEFAULT_WINDOW_SECONDS));

        String body = String.format(
                "{\"success\":false,\"code\":429,\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"Quá nhiều yêu cầu. Vui lòng thử lại sau %d giây.\",\"path\":\"%s\"}",
                DEFAULT_WINDOW_SECONDS,
                uri
        );
        response.getWriter().write(body);
    }
}

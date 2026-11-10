package com.ecommerce.service.impl;

import com.ecommerce.config.AppProperties;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.service.OtpThrottleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpThrottleServiceImpl implements OtpThrottleService {

    private final StringRedisTemplate stringRedisTemplate;
    private final AppProperties appProperties;

    private static final String COOLDOWN_KEY_PREFIX = "otp_cooldown:";
    private static final String HOURLY_KEY_PREFIX = "otp_hourly:";

    @Override
    public void checkAndRecordOtpRequest(String email, String purpose) {
        String normalizedEmail = email != null ? email.trim().toLowerCase(Locale.ROOT) : "";
        int cooldownSeconds = (appProperties.getAuth() != null)
                ? appProperties.getAuth().getOtpCooldownSeconds()
                : 60;
        int maxPerHour = (appProperties.getAuth() != null)
                ? appProperties.getAuth().getOtpMaxPerHour()
                : 5;

        String cooldownKey = COOLDOWN_KEY_PREFIX + purpose + ":" + normalizedEmail;
        String hourlyKey = HOURLY_KEY_PREFIX + purpose + ":" + normalizedEmail;

        try {
            // N5: Cooldown dùng setIfAbsent atomic (false -> OTP_THROTTLED)
            Boolean acquiredCooldown = stringRedisTemplate.opsForValue()
                    .setIfAbsent(cooldownKey, "1", Duration.ofSeconds(cooldownSeconds));
            if (!Boolean.TRUE.equals(acquiredCooldown)) {
                Long expire = stringRedisTemplate.getExpire(cooldownKey);
                long remainingSeconds = (expire != null && expire > 0) ? expire : cooldownSeconds;
                log.warn("Yêu cầu gửi OTP cho [{}] (purpose={}) bị chặn do đang trong thời gian cooldown (còn {}s)",
                        normalizedEmail, purpose, remainingSeconds);
                throw new AppException(ErrorCode.OTP_THROTTLED,
                        "Vui lòng đợi " + remainingSeconds + " giây trước khi yêu cầu mã OTP mới.");
            }

            // N5: Tăng bộ đếm giờ bằng increment rồi mới so sánh, đảm bảo luôn đặt expire khi key chưa có TTL
            Long newCount = stringRedisTemplate.opsForValue().increment(hourlyKey);
            long currentCount = newCount != null ? newCount : 1L;

            Long currentTtl = stringRedisTemplate.getExpire(hourlyKey);
            if (currentCount == 1L || (currentTtl != null && currentTtl < 0)) {
                stringRedisTemplate.expire(hourlyKey, Duration.ofHours(1));
            }

            if (currentCount > maxPerHour) {
                log.warn("Yêu cầu gửi OTP cho [{}] (purpose={}) vượt quá trần {} OTP/giờ",
                        normalizedEmail, purpose, maxPerHour);
                throw new AppException(ErrorCode.OTP_THROTTLED,
                        "Bạn đã vượt quá giới hạn " + maxPerHour + " mã OTP trong vòng 1 giờ. Vui lòng thử lại sau.");
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi khi kiểm tra throttle OTP trên Redis (fail-closed): {}", e.getMessage());
            throw new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE, "Dịch vụ xác thực tạm thời không khả dụng, vui lòng thử lại sau.");
        }
    }
}

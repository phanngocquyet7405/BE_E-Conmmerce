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
            // 1. Kiểm tra Cooldown
            Boolean hasCooldown = stringRedisTemplate.hasKey(cooldownKey);
            if (Boolean.TRUE.equals(hasCooldown)) {
                Long expire = stringRedisTemplate.getExpire(cooldownKey);
                long remainingSeconds = (expire != null && expire > 0) ? expire : cooldownSeconds;
                log.warn("Yêu cầu gửi OTP cho [{}] (purpose={}) bị chặn do đang trong thời gian cooldown (còn {}s)",
                        normalizedEmail, purpose, remainingSeconds);
                throw new AppException(ErrorCode.OTP_THROTTLED,
                        "Vui lòng đợi " + remainingSeconds + " giây trước khi yêu cầu mã OTP mới.");
            }

            // 2. Kiểm tra giới hạn số lần gửi / giờ
            String hourlyCountStr = stringRedisTemplate.opsForValue().get(hourlyKey);
            int currentHourlyCount = hourlyCountStr != null ? Integer.parseInt(hourlyCountStr) : 0;
            if (currentHourlyCount >= maxPerHour) {
                log.warn("Yêu cầu gửi OTP cho [{}] (purpose={}) vượt quá trần {} OTP/giờ",
                        normalizedEmail, purpose, maxPerHour);
                throw new AppException(ErrorCode.OTP_THROTTLED,
                        "Bạn đã vượt quá giới hạn " + maxPerHour + " mã OTP trong vòng 1 giờ. Vui lòng thử lại sau.");
            }

            // 3. Ghi nhận cooldown & tăng bộ đếm giờ
            stringRedisTemplate.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(cooldownSeconds));
            Long newCount = stringRedisTemplate.opsForValue().increment(hourlyKey);
            if (newCount != null && newCount == 1L) {
                stringRedisTemplate.expire(hourlyKey, Duration.ofHours(1));
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi khi kiểm tra throttle OTP trên Redis (fail-closed): {}", e.getMessage());
            throw new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE, "Dịch vụ xác thực tạm thời không khả dụng, vui lòng thử lại sau.");
        }
    }
}

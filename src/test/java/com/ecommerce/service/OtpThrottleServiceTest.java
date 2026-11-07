package com.ecommerce.service;

import com.ecommerce.config.AppProperties;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.service.impl.OtpThrottleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpThrottleServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private OtpThrottleServiceImpl otpThrottleService;

    @BeforeEach
    void setUp() {
        AppProperties.Auth auth = new AppProperties.Auth();
        auth.setOtpCooldownSeconds(60);
        auth.setOtpMaxPerHour(5);
        when(appProperties.getAuth()).thenReturn(auth);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Thành công: Chưa có cooldown và chưa quá 5 OTP/giờ -> Cho phép và ghi nhận Redis")
    void checkAndRecordOtpRequest_Success() {
        String email = "test@gmail.com";
        String purpose = "verify-account";

        when(stringRedisTemplate.hasKey("otp_cooldown:" + purpose + ":" + email)).thenReturn(false);
        when(valueOperations.get("otp_hourly:" + purpose + ":" + email)).thenReturn("2");
        when(valueOperations.increment("otp_hourly:" + purpose + ":" + email)).thenReturn(3L);

        assertDoesNotThrow(() -> otpThrottleService.checkAndRecordOtpRequest(email, purpose));

        verify(valueOperations, times(1)).set(eq("otp_cooldown:" + purpose + ":" + email), eq("1"), eq(Duration.ofSeconds(60)));
        verify(valueOperations, times(1)).increment("otp_hourly:" + purpose + ":" + email);
    }

    @Test
    @DisplayName("Bị chặn Cooldown: Còn thời gian cooldown -> Ném OTP_THROTTLED")
    void checkAndRecordOtpRequest_CooldownActive_ThrowsException() {
        String email = "test@gmail.com";
        String purpose = "verify-account";

        when(stringRedisTemplate.hasKey("otp_cooldown:" + purpose + ":" + email)).thenReturn(true);
        when(stringRedisTemplate.getExpire("otp_cooldown:" + purpose + ":" + email)).thenReturn(45L);

        AppException ex = assertThrows(AppException.class, () -> otpThrottleService.checkAndRecordOtpRequest(email, purpose));
        assertEquals(ErrorCode.OTP_THROTTLED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("45 giây"));
    }

    @Test
    @DisplayName("Bị chặn Hourly: Đã đạt trần 5 OTP/giờ -> Ném OTP_THROTTLED")
    void checkAndRecordOtpRequest_HourlyExceeded_ThrowsException() {
        String email = "test@gmail.com";
        String purpose = "verify-account";

        when(stringRedisTemplate.hasKey("otp_cooldown:" + purpose + ":" + email)).thenReturn(false);
        when(valueOperations.get("otp_hourly:" + purpose + ":" + email)).thenReturn("5");

        AppException ex = assertThrows(AppException.class, () -> otpThrottleService.checkAndRecordOtpRequest(email, purpose));
        assertEquals(ErrorCode.OTP_THROTTLED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("5 mã OTP trong vòng 1 giờ"));
    }

    @Test
    @DisplayName("Fail-closed: Khi Redis gặp sự cố -> Ném AUTH_SERVICE_UNAVAILABLE")
    void checkAndRecordOtpRequest_RedisDown_ThrowsServiceUnavailable() {
        String email = "test@gmail.com";
        String purpose = "verify-account";

        when(stringRedisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis connection refused"));

        AppException ex = assertThrows(AppException.class, () -> otpThrottleService.checkAndRecordOtpRequest(email, purpose));
        assertEquals(ErrorCode.AUTH_SERVICE_UNAVAILABLE, ex.getErrorCode());
    }
}

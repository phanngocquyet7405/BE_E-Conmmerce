package com.ecommerce.service.impl;

import com.ecommerce.config.AppProperties;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.service.RedisTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisTokenServiceImpl implements RedisTokenService {

    private static final String REFRESH_KEY_PREFIX = "refresh_token:";
    private static final String BLACKLIST_KEY_PREFIX = "blacklist_token:";
    private static final String BLOCKED_USER_PREFIX = "blocked:user:";
    private static final String USED_REFRESH_PREFIX = "used-refresh:";
    private static final String RESET_TOKEN_PREFIX = "reset-token:";
    private static final String RESET_TOKEN_USER_PREFIX = "reset-token-user:";
    private static final String LOGIN_FAIL_IP_PREFIX = "login_fail_ip:";
    private static final String LOGIN_FAIL_EMAIL_PREFIX = "login_fail_email:";
    private static final String OTP_KEY_PREFIX = "otp:";
    private static final String OTP_ATTEMPTS_PREFIX = "otp_attempts:";
    private static final String OTP_FAILED_HOURLY_PREFIX = "otp_failed_hourly:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final AppProperties appProperties;

    @Override
    public void saveRefreshToken(String userId, String tokenId, long ttlMillis) {
        try {
            String key = REFRESH_KEY_PREFIX + userId + ":" + tokenId;
            redisTemplate.opsForValue().set(key, "ACTIVE", ttlMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Không thể lưu refresh token vào Redis: {}", e.getMessage());
        }
    }

    @Override
    public boolean isRefreshTokenValid(String userId, String tokenId) {
        try {
            String key = REFRESH_KEY_PREFIX + userId + ":" + tokenId;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("Lỗi khi kết nối Redis để kiểm tra refresh token: {}. Áp dụng Fail-Closed từ chối phiên.", e.getMessage());
            return false; // Fail-Closed bảo mật: không cho phép token nếu Redis không xác thực được
        }
    }

    @Override
    public void deleteRefreshToken(String userId, String tokenId) {
        try {
            String key = REFRESH_KEY_PREFIX + userId + ":" + tokenId;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Không thể xóa refresh token trên Redis: {}", e.getMessage());
        }
    }

    @Override
    public boolean deleteRefreshTokenAtomic(String userId, String tokenId) {
        try {
            String key = REFRESH_KEY_PREFIX + userId + ":" + tokenId;
            Boolean deleted = redisTemplate.delete(key);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            log.error("Lỗi khi xóa refresh token atomic trên Redis (fail-closed): {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void markRefreshTokenAsUsed(String userId, String tokenId, long remainingTtlMillis) {
        try {
            if (remainingTtlMillis > 0) {
                String key = USED_REFRESH_PREFIX + userId + ":" + tokenId;
                redisTemplate.opsForValue().set(key, "USED", remainingTtlMillis, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            log.warn("Không thể ghi dấu refresh token đã dùng trên Redis: {}", e.getMessage());
        }
    }

    @Override
    public boolean isRefreshTokenUsed(String userId, String tokenId) {
        try {
            String key = USED_REFRESH_PREFIX + userId + ":" + tokenId;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("Lỗi kiểm tra refresh token đã dùng trên Redis: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void deleteAllRefreshTokensForUser(String userId) {
        try {
            String pattern = REFRESH_KEY_PREFIX + userId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Đã thu hồi {} refresh token của người dùng {}", keys.size(), userId);
            }
        } catch (Exception e) {
            log.warn("Không thể xóa toàn bộ refresh token của user {} trên Redis: {}", userId, e.getMessage());
        }
    }

    @Override
    public void revokeAllSessions(String userId) {
        try {
            blockUser(userId);
            log.info("Đã thu hồi toàn bộ phiên đăng nhập (refresh token & access token iat) cho user {}", userId);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi khi thu hồi toàn bộ phiên cho user {} trên Redis: {}", userId, e.getMessage());
            throw new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE, "Không thể thu hồi phiên đăng nhập do lỗi kết nối Redis");
        }
    }

    @Override
    public void blacklistAccessToken(String accessToken, long remainingTtlMillis) {
        try {
            if (remainingTtlMillis > 0) {
                String key = BLACKLIST_KEY_PREFIX + accessToken;
                redisTemplate.opsForValue().set(key, "REVOKED", remainingTtlMillis, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            log.warn("Không thể ghi blacklist token trên Redis: {}", e.getMessage());
        }
    }

    @Override
    public boolean isAccessTokenBlacklisted(String accessToken) {
        try {
            String key = BLACKLIST_KEY_PREFIX + accessToken;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("Lỗi kiểm tra blacklist token trên Redis: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void blockUser(String userId) {
        try {
            long epochSeconds = Instant.now().getEpochSecond();
            long ttlMillis = (appProperties.getJwt() != null)
                    ? appProperties.getJwt().getAccessExpirationMs()
                    : 3600000L;

            String key = BLOCKED_USER_PREFIX + userId;
            // Ghi giá trị là epoch giây lúc gọi, TTL = TTL access token
            redisTemplate.opsForValue().set(key, String.valueOf(epochSeconds), ttlMillis, TimeUnit.MILLISECONDS);
            deleteAllRefreshTokensForUser(userId);
            log.info("Đã ghi Redis blocked:user:{} với timestamp={} (TTL={}ms) và thu hồi refresh token",
                    userId, epochSeconds, ttlMillis);
        } catch (Exception e) {
            log.error("Không thể ghi key blocked user vào Redis: {}", e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể áp dụng khóa/đổi quyền ngay, vui lòng thử lại");
        }
    }

    @Override
    public void unblockUser(String userId) {
        try {
            String key = BLOCKED_USER_PREFIX + userId;
            redisTemplate.delete(key);
            log.info("Đã xóa key blocked:user:{} trên Redis (mở khóa)", userId);
        } catch (Exception e) {
            log.warn("Không thể xóa key blocked user trên Redis: {}", e.getMessage());
        }
    }

    @Override
    public boolean isUserBlocked(String userId, long issuedAtSeconds) {
        try {
            String key = BLOCKED_USER_PREFIX + userId;
            Object val = redisTemplate.opsForValue().get(key);
            if (val == null) {
                return false;
            }
            long blockedAtEpochSeconds = Long.parseLong(val.toString());
            // Trả về true khi key tồn tại và token được cấp TRƯỚC mốc khóa
            return issuedAtSeconds < blockedAtEpochSeconds;
        } catch (Exception e) {
            log.warn("Lỗi kiểm tra blocked user trên Redis (fail-open): {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void saveOtp(String key, String otp, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(OTP_KEY_PREFIX + key, otp, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Không thể lưu OTP vào Redis (fail-closed): {}", e.getMessage());
            throw new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE, "Dịch vụ xác thực tạm thời không khả dụng, vui lòng thử lại sau");
        }
    }

    @Override
    public String getOtp(String key) {
        try {
            Object val = redisTemplate.opsForValue().get(OTP_KEY_PREFIX + key);
            return val != null ? val.toString() : null;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Không thể đọc OTP từ Redis (fail-closed): {}", e.getMessage());
            throw new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE, "Dịch vụ xác thực tạm thời không khả dụng, vui lòng thử lại sau");
        }
    }

    @Override
    public void deleteOtp(String key) {
        try {
            redisTemplate.delete(OTP_KEY_PREFIX + key);
        } catch (Exception e) {
            log.warn("Không thể xóa OTP trên Redis: {}", e.getMessage());
        }
    }

    @Override
    public int incrementOtpAttempts(String key, long ttlSeconds) {
        try {
            String redisKey = OTP_ATTEMPTS_PREFIX + key;
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(redisKey, ttlSeconds, TimeUnit.SECONDS);
            }
            return count != null ? count.intValue() : 1;
        } catch (Exception e) {
            log.error("Lỗi khi tăng số lần nhập sai OTP (fail-closed): {}", e.getMessage());
            throw new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE, "Dịch vụ xác thực tạm thời không khả dụng, vui lòng thử lại sau");
        }
    }

    @Override
    public void resetOtpAttempts(String key) {
        try {
            redisTemplate.delete(OTP_ATTEMPTS_PREFIX + key);
        } catch (Exception ignored) {
        }
    }

    @Override
    public int incrementHourlyFailedAttempts(String purpose, String email, long ttlSeconds) {
        try {
            String key = OTP_FAILED_HOURLY_PREFIX + purpose + ":" + email;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
            }
            return count != null ? count.intValue() : 1;
        } catch (Exception e) {
            log.error("Lỗi khi tăng số lần đoán sai OTP theo giờ (fail-closed): {}", e.getMessage());
            throw new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE, "Dịch vụ xác thực tạm thời không khả dụng, vui lòng thử lại sau");
        }
    }

    @Override
    public int getHourlyFailedAttempts(String purpose, String email) {
        try {
            String key = OTP_FAILED_HOURLY_PREFIX + purpose + ":" + email;
            Object val = redisTemplate.opsForValue().get(key);
            if (val == null) {
                return 0;
            }
            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            log.error("Lỗi khi đọc số lần đoán sai OTP theo giờ (fail-closed): {}", e.getMessage());
            throw new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE, "Dịch vụ xác thực tạm thời không khả dụng, vui lòng thử lại sau");
        }
    }

    @Override
    public void savePasswordResetToken(String userId, String tokenHash, long ttlSeconds) {
        try {
            String userKey = RESET_TOKEN_USER_PREFIX + userId;
            Object oldHash = redisTemplate.opsForValue().get(userKey);
            if (oldHash != null) {
                redisTemplate.delete(RESET_TOKEN_PREFIX + oldHash);
            }

            String tokenKey = RESET_TOKEN_PREFIX + tokenHash;
            redisTemplate.opsForValue().set(tokenKey, userId, ttlSeconds, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(userKey, tokenHash, ttlSeconds, TimeUnit.SECONDS);
            log.info("Đã lưu password reset token hash cho user {} (TTL={}s)", userId, ttlSeconds);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi khi lưu password reset token vào Redis (fail-closed): {}", e.getMessage());
            throw new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE, "Dịch vụ xác thực tạm thời không khả dụng, vui lòng thử lại sau");
        }
    }

    @Override
    public String consumePasswordResetTokenAtomic(String tokenHash) {
        try {
            String tokenKey = RESET_TOKEN_PREFIX + tokenHash;
            Object userIdVal = redisTemplate.opsForValue().getAndDelete(tokenKey);
            if (userIdVal == null) {
                return null;
            }
            String userId = userIdVal.toString();
            redisTemplate.delete(RESET_TOKEN_USER_PREFIX + userId);
            return userId;
        } catch (Exception e) {
            log.error("Lỗi khi tiêu thụ atomic password reset token trên Redis (fail-closed): {}", e.getMessage());
            throw new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE, "Dịch vụ xác thực tạm thời không khả dụng, vui lòng thử lại sau");
        }
    }

    @Override
    public int getLoginFailAttemptsByIp(String email, String ip) {
        try {
            String key = LOGIN_FAIL_IP_PREFIX + email + ":" + (ip != null ? ip : "unknown");
            Object val = redisTemplate.opsForValue().get(key);
            return val != null ? Integer.parseInt(val.toString()) : 0;
        } catch (Exception e) {
            log.warn("Lỗi khi đọc số lần đăng nhập sai theo IP trên Redis (fail-open): {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public int getLoginFailAttemptsByEmail(String email) {
        try {
            String key = LOGIN_FAIL_EMAIL_PREFIX + email;
            Object val = redisTemplate.opsForValue().get(key);
            return val != null ? Integer.parseInt(val.toString()) : 0;
        } catch (Exception e) {
            log.warn("Lỗi khi đọc số lần đăng nhập sai theo Email trên Redis (fail-open): {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public void recordLoginFailure(String email, String ip, long ttlSeconds) {
        try {
            String ipKey = LOGIN_FAIL_IP_PREFIX + email + ":" + (ip != null ? ip : "unknown");
            Long countIp = redisTemplate.opsForValue().increment(ipKey);
            if (countIp != null && countIp == 1L) {
                redisTemplate.expire(ipKey, ttlSeconds, TimeUnit.SECONDS);
            }

            String emailKey = LOGIN_FAIL_EMAIL_PREFIX + email;
            Long countEmail = redisTemplate.opsForValue().increment(emailKey);
            if (countEmail != null && countEmail == 1L) {
                redisTemplate.expire(emailKey, ttlSeconds, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("Lỗi khi ghi nhận đăng nhập sai trên Redis (fail-open): {}", e.getMessage());
        }
    }

    @Override
    public void resetLoginFailure(String email, String ip) {
        try {
            String ipKey = LOGIN_FAIL_IP_PREFIX + email + ":" + (ip != null ? ip : "unknown");
            redisTemplate.delete(ipKey);
        } catch (Exception e) {
            log.warn("Lỗi khi reset bộ đếm đăng nhập sai theo IP trên Redis: {}", e.getMessage());
        }
    }
}

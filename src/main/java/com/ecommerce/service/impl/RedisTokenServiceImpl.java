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
            long ttlMillis = (appProperties != null && appProperties.getJwt() != null)
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
}

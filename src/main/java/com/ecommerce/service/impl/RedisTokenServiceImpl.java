package com.ecommerce.service.impl;

import com.ecommerce.service.RedisTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisTokenServiceImpl implements RedisTokenService {

    private static final String REFRESH_KEY_PREFIX = "refresh_token:";
    private static final String BLACKLIST_KEY_PREFIX = "blacklist_token:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void saveRefreshToken(String userId, String tokenId, long ttlMillis) {
        try {
            String key = REFRESH_KEY_PREFIX + userId + ":" + tokenId;
            redisTemplate.opsForValue().set(key, "ACTIVE", ttlMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Lưu ý: Không thể kết nối tới Redis (localhost:6379 / Upstash) để lưu refresh token. Đăng nhập vẫn thành công: {}", e.getMessage());
        }
    }

    @Override
    public boolean isRefreshTokenValid(String userId, String tokenId) {
        try {
            String key = REFRESH_KEY_PREFIX + userId + ":" + tokenId;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("Lưu ý: Không thể kết nối tới Redis để kiểm tra refresh token: {}", e.getMessage());
            return true; // Fallback nếu Redis chưa chạy
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
            log.warn("Lưu ý: Không thể kết nối tới Redis để kiểm tra blacklist token (fallback cho phép xác thực JWT thông thường): {}", e.getMessage());
            return false;
        }
    }
}

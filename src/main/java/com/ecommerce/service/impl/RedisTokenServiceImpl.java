package com.ecommerce.service.impl;

import com.ecommerce.service.RedisTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisTokenServiceImpl implements RedisTokenService {

    private static final String REFRESH_KEY_PREFIX = "refresh_token:";
    private static final String BLACKLIST_KEY_PREFIX = "blacklist_token:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void saveRefreshToken(String userId, String tokenId, long ttlMillis) {
        String key = REFRESH_KEY_PREFIX + userId + ":" + tokenId;
        redisTemplate.opsForValue().set(key, "ACTIVE", ttlMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isRefreshTokenValid(String userId, String tokenId) {
        String key = REFRESH_KEY_PREFIX + userId + ":" + tokenId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public void deleteRefreshToken(String userId, String tokenId) {
        String key = REFRESH_KEY_PREFIX + userId + ":" + tokenId;
        redisTemplate.delete(key);
    }

    @Override
    public void blacklistAccessToken(String accessToken, long remainingTtlMillis) {
        if (remainingTtlMillis > 0) {
            String key = BLACKLIST_KEY_PREFIX + accessToken;
            redisTemplate.opsForValue().set(key, "REVOKED", remainingTtlMillis, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public boolean isAccessTokenBlacklisted(String accessToken) {
        String key = BLACKLIST_KEY_PREFIX + accessToken;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}

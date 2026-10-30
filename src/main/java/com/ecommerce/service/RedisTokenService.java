package com.ecommerce.service;

public interface RedisTokenService {
    void saveRefreshToken(String userId, String tokenId, long ttlMillis);
    boolean isRefreshTokenValid(String userId, String tokenId);
    void deleteRefreshToken(String userId, String tokenId);
    void deleteAllRefreshTokensForUser(String userId);
    void blacklistAccessToken(String accessToken, long remainingTtlMillis);
    boolean isAccessTokenBlacklisted(String accessToken);
    void blockUser(String userId);
    void unblockUser(String userId);
    boolean isUserBlocked(String userId, long issuedAtSeconds);
}

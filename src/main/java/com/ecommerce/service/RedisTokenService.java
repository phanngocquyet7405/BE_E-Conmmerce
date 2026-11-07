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

    // Quản lý OTP
    void saveOtp(String key, String otp, long ttlSeconds);
    String getOtp(String key);
    void deleteOtp(String key);

    // Chống brute-force OTP
    int incrementOtpAttempts(String key, long ttlSeconds);
    void resetOtpAttempts(String key);
}

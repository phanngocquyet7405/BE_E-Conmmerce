package com.ecommerce.service;

public interface RedisTokenService {
    void saveRefreshToken(String userId, String tokenId, long ttlMillis);
    boolean isRefreshTokenValid(String userId, String tokenId);
    void deleteRefreshToken(String userId, String tokenId);
    boolean deleteRefreshTokenAtomic(String userId, String tokenId);
    void markRefreshTokenAsUsed(String userId, String tokenId, long remainingTtlMillis);
    boolean isRefreshTokenUsed(String userId, String tokenId);
    void deleteAllRefreshTokensForUser(String userId);
    void revokeAllSessions(String userId);
    void blacklistAccessToken(String accessToken, long remainingTtlMillis);
    boolean isAccessTokenBlacklisted(String accessToken);
    void blockUser(String userId);
    void unblockUser(String userId);
    boolean isUserBlocked(String userId, long issuedAtSeconds);
    boolean isUserBlockedFailClosed(String userId, long issuedAtSeconds);

    // Quản lý OTP
    void saveOtp(String key, String otp, long ttlSeconds);
    String getOtp(String key);
    void deleteOtp(String key);

    // Chống brute-force OTP
    int incrementOtpAttempts(String key, long ttlSeconds);
    void resetOtpAttempts(String key);
    int incrementHourlyFailedAttempts(String purpose, String email, long ttlSeconds);
    int getHourlyFailedAttempts(String purpose, String email);

    // Password Reset Token (Cách B)
    void savePasswordResetToken(String userId, String tokenHash, long ttlSeconds);
    String peekPasswordResetToken(String tokenHash);
    String consumePasswordResetTokenAtomic(String tokenHash);

    // Khóa tạm tài khoản khi đăng nhập sai nhiều lần (T13)
    int getLoginFailAttemptsByIp(String email, String ip);
    int getLoginFailAttemptsByEmail(String email);
    void recordLoginFailure(String email, String ip, long ttlSeconds);
    void resetLoginFailure(String email, String ip);
}

package com.ecommerce.util;

import com.ecommerce.config.AppProperties;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.security.CustomUserDetails;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private AppProperties appProperties;

    // 32-byte Base64 encoded keys (256-bit HMAC-SHA256)
    private final String validAccessSecret = Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes());
    private final String validRefreshSecret = Base64.getEncoder().encodeToString("abcdefghijklmnopqrstuvwxyz123456".getBytes());

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getJwt().setIssuer("ecommerce-api-test");
        appProperties.getJwt().setAccessSecret(validAccessSecret);
        appProperties.getJwt().setRefreshSecret(validRefreshSecret);
        appProperties.getJwt().setAccessExpirationMs(3600000L); // 1 hour
        appProperties.getJwt().setRefreshExpirationMs(86400000L); // 1 day

        jwtUtil = new JwtUtil(appProperties);
        jwtUtil.initKeys();
    }

    @Test
    @DisplayName("Khởi tạo JwtUtil thành công khi có đủ secret Base64 hợp lệ (>= 256 bits)")
    void initKeys_Success() {
        assertNotNull(jwtUtil);
    }

    @Test
    @DisplayName("Ném IllegalStateException khi thiếu hoặc secret không đủ 256-bit")
    void initKeys_WeakSecret_ThrowsException() {
        AppProperties invalidProps = new AppProperties();
        // Secret chỉ có 10 ký tự (< 32 bytes)
        invalidProps.getJwt().setAccessSecret(Base64.getEncoder().encodeToString("short-key".getBytes()));
        invalidProps.getJwt().setRefreshSecret(validRefreshSecret);

        JwtUtil invalidJwtUtil = new JwtUtil(invalidProps);
        assertThrows(IllegalStateException.class, invalidJwtUtil::initKeys);
    }

    @Test
    @DisplayName("Sinh Access Token và trích xuất đúng thông tin claims")
    void generateAccessToken_AndValidateClaims_Success() {
        CustomUserDetails userDetails = new CustomUserDetails(
                "user-123",
                "testuser@ecommerce.com",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        String token = jwtUtil.generateAccessToken(userDetails);
        assertNotNull(token);

        Claims claims = jwtUtil.verifyAccessToken(token);
        assertEquals("testuser@ecommerce.com", claims.getSubject());
        assertEquals("user-123", claims.get(JwtUtil.CLAIM_USER_ID, String.class));
        assertEquals(JwtUtil.TYPE_ACCESS, claims.get(JwtUtil.CLAIM_TOKEN_TYPE, String.class));

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get(JwtUtil.CLAIM_ROLES, List.class);
        assertTrue(roles.contains("ROLE_USER"));
    }

    @Test
    @DisplayName("Sinh Refresh Token và trích xuất đúng thông tin tokenId")
    void generateRefreshToken_Success() {
        CustomUserDetails userDetails = new CustomUserDetails(
                "user-123",
                "testuser@ecommerce.com",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        String tokenId = "refresh-uuid-999";
        String refreshToken = jwtUtil.generateRefreshToken(userDetails, tokenId);

        assertNotNull(refreshToken);

        Claims claims = jwtUtil.verifyRefreshToken(refreshToken);
        assertEquals(tokenId, claims.getId());
        assertEquals("user-123", claims.get(JwtUtil.CLAIM_USER_ID, String.class));
        assertEquals(JwtUtil.TYPE_REFRESH, claims.get(JwtUtil.CLAIM_TOKEN_TYPE, String.class));
    }

    @Test
    @DisplayName("Từ chối Access Token khi bị sửa đổi chữ ký")
    void verifyAccessToken_TamperedToken_ThrowsInvalidToken() {
        CustomUserDetails userDetails = new CustomUserDetails(
                "user-123",
                "testuser@ecommerce.com",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        String token = jwtUtil.generateAccessToken(userDetails);
        String tamperedToken = token.substring(0, token.length() - 5) + "abcde";

        AppException ex = assertThrows(AppException.class, () -> jwtUtil.verifyAccessToken(tamperedToken));
        assertEquals(ErrorCode.INVALID_TOKEN, ex.getErrorCode());
    }
}

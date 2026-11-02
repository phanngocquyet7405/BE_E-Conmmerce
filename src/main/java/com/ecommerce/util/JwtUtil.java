package com.ecommerce.util;

import com.ecommerce.config.AppProperties;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.security.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TOKEN_TYPE = "typ";
    public static final String TYPE_ACCESS = "ACCESS";
    public static final String TYPE_REFRESH = "REFRESH";

    private final AppProperties appProperties;
    private SecretKey accessKey;
    private SecretKey refreshKey;

    @PostConstruct
    public void initKeys() {
        String accessSecret = appProperties.getJwt().getAccessSecret();
        String refreshSecret = appProperties.getJwt().getRefreshSecret();

        if (!StringUtils.hasText(accessSecret) || !StringUtils.hasText(refreshSecret)) {
            throw new IllegalStateException(
                    "Cấu hình JWT bị thiếu! Biến môi trường JWT_ACCESS_SECRET và JWT_REFRESH_SECRET là bắt buộc và phải là chuỗi Base64 >= 256-bit."
            );
        }

        try {
            byte[] accessBytes = Decoders.BASE64.decode(accessSecret.trim());
            byte[] refreshBytes = Decoders.BASE64.decode(refreshSecret.trim());

            if (accessBytes.length < 32 || refreshBytes.length < 32) {
                throw new IllegalArgumentException("Khóa bí mật JWT phải có độ dài tối thiểu 256 bits (32 bytes sau khi decode Base64).");
            }

            this.accessKey = Keys.hmacShaKeyFor(accessBytes);
            this.refreshKey = Keys.hmacShaKeyFor(refreshBytes);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Không thể khởi tạo khóa JWT. Vui lòng đảm bảo JWT_ACCESS_SECRET và JWT_REFRESH_SECRET là chuỗi Base64 hợp lệ (>= 256 bits). Chi tiết: " + ex.getMessage(),
                    ex
            );
        }
    }

    public String generateAccessToken(CustomUserDetails userDetails) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(appProperties.getJwt().getAccessExpirationMs());

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())
                .issuer(appProperties.getJwt().getIssuer())
                .subject(userDetails.getUsername())
                .claim(CLAIM_USER_ID, userDetails.getId())
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(accessKey, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(CustomUserDetails userDetails, String tokenId) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(appProperties.getJwt().getRefreshExpirationMs());

        return Jwts.builder()
                .id(tokenId)
                .issuer(appProperties.getJwt().getIssuer())
                .subject(userDetails.getUsername())
                .claim(CLAIM_USER_ID, userDetails.getId())
                .claim(CLAIM_TOKEN_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(refreshKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims verifyAccessToken(String token) {
        Claims claims = parseToken(token, accessKey);
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            throw new AppException(ErrorCode.INVALID_TOKEN, "Token không phải là Access Token");
        }
        return claims;
    }

    public Claims verifyRefreshToken(String token) {
        Claims claims = parseToken(token, refreshKey);
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            throw new AppException(ErrorCode.INVALID_TOKEN, "Token không phải là Refresh Token");
        }
        return claims;
    }

    private Claims parseToken(String token, SecretKey secretKey) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(appProperties.getJwt().getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }
}

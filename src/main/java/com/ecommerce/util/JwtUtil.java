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
        this.accessKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(appProperties.getJwt().getAccessSecret()));
        this.refreshKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(appProperties.getJwt().getRefreshSecret()));
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

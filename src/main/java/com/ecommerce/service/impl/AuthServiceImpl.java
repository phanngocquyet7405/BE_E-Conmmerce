package com.ecommerce.service.impl;

import com.ecommerce.common.constant.RoleEnum;
import com.ecommerce.config.AppProperties;
import com.ecommerce.document.User;
import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.request.RefreshTokenRequest;
import com.ecommerce.dto.request.RegisterRequest;
import com.ecommerce.dto.response.TokenResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.security.CustomUserDetails;
import com.ecommerce.service.AuthService;
import com.ecommerce.service.RedisTokenService;
import com.ecommerce.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RedisTokenService redisTokenService;
    private final AppProperties appProperties;

    @Override
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .roles(Set.of(RoleEnum.ROLE_CUSTOMER))
                .active(true)
                .build();

        userRepository.save(user);
        CustomUserDetails userDetails = new CustomUserDetails(user);

        return buildAndSaveTokens(userDetails);
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return buildAndSaveTokens(userDetails);
    }

    @Override
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        Claims claims = jwtUtil.verifyRefreshToken(request.getRefreshToken());
        String userId = claims.get(JwtUtil.CLAIM_USER_ID, String.class);
        String tokenId = claims.getId();

        if (!redisTokenService.isRefreshTokenValid(userId, tokenId)) {
            throw new AppException(ErrorCode.TOKEN_REVOKED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        // Thu hồi refresh token cũ (Token Rotation)
        redisTokenService.deleteRefreshToken(userId, tokenId);

        CustomUserDetails userDetails = new CustomUserDetails(user);
        return buildAndSaveTokens(userDetails);
    }

    @Override
    public void logout(String accessToken) {
        try {
            Claims claims = jwtUtil.verifyAccessToken(accessToken);
            Date expiration = claims.getExpiration();
            long remainingTtl = expiration.getTime() - System.currentTimeMillis();

            if (remainingTtl > 0) {
                redisTokenService.blacklistAccessToken(accessToken, remainingTtl);
            }
        } catch (Exception ignored) {
            // Token không hợp lệ hoặc đã hết hạn thì không cần xử lý thêm
        }
    }

    private TokenResponse buildAndSaveTokens(CustomUserDetails userDetails) {
        String accessToken = jwtUtil.generateAccessToken(userDetails);
        String tokenId = UUID.randomUUID().toString();
        String refreshToken = jwtUtil.generateRefreshToken(userDetails, tokenId);

        // Lưu vào Redis với TTL
        redisTokenService.saveRefreshToken(
                userDetails.getId(), tokenId, appProperties.getJwt().getRefreshExpirationMs());

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(appProperties.getJwt().getAccessExpirationMs() / 1000)
                .build();
    }
}

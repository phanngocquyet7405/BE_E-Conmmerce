package com.ecommerce.service.impl;

import com.ecommerce.common.constant.RoleEnum;
import com.ecommerce.config.AppProperties;
import com.ecommerce.document.User;
import com.ecommerce.dto.request.*;
import com.ecommerce.dto.response.TokenResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.security.CustomUserDetails;
import com.ecommerce.service.AuthService;
import com.ecommerce.service.EmailService;
import com.ecommerce.service.RedisTokenService;
import com.ecommerce.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RedisTokenService redisTokenService;
    private final EmailService emailService;
    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 1. Đăng ký tài khoản:
     * - Lưu thông tin User với isActive = false
     * - Sinh mã OTP 6 số ngẫu nhiên thời hạn 5 phút
     * - Gửi email chứa mã OTP qua JavaMailSender
     */
    @Override
    public void register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        // Sinh mã OTP 6 số ngẫu nhiên
        String otp = generateOtp();
        Instant otpExpiryTime = Instant.now().plus(5, ChronoUnit.MINUTES);

        // Lưu vào Database với isActive = false
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .roles(Set.of(RoleEnum.ROLE_CUSTOMER))
                .active(false) // Trạng thái chưa kích hoạt
                .emailVerified(false)
                .phoneVerified(false)
                .otp(otp)
                .otpExpiryTime(otpExpiryTime)
                .build();

        userRepository.save(user);

        // Lưu cache OTP vào Redis với thời hạn 300s (5 phút)
        try {
            redisTokenService.saveOtp("verify-account:" + email, otp, 300);
        } catch (Exception ex) {
            log.warn("Không thể lưu OTP vào Redis: {}", ex.getMessage());
        }

        // 2. Gửi email chứa mã OTP xác thực qua JavaMailSender
        emailService.sendOtpEmail(email, otp);
        log.info("Đã tạo tài khoản cho {} và gửi OTP xác thực thành công.", email);
    }

    /**
     * 3. Xác thực Email bằng mã OTP:
     * - Kiểm tra mã OTP và thời hạn 5 phút
     * - Nếu hợp lệ: Cập nhật isActive = true, emailVerified = true
     */
    @Override
    public boolean verifyOtp(VerifyOtpRequest request) {
        String email = request.getTargetEmail();
        if (!StringUtils.hasText(email)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Vui lòng cung cấp email cần xác thực");
        }
        email = email.toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_CREDENTIALS, "Tài khoản không tồn tại"));

        if (user.isActive()) {
            return true;
        }

        String inputOtp = request.getOtp().trim();

        // Kiểm tra OTP từ database hoặc Redis
        boolean otpMatches = inputOtp.equals(user.getOtp());
        if (!otpMatches) {
            String redisOtp = redisTokenService.getOtp("verify-account:" + email);
            if (redisOtp != null && redisOtp.equals(inputOtp)) {
                otpMatches = true;
            }
        }

        if (!otpMatches) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Mã OTP không chính xác hoặc đã hết hạn");
        }

        if (user.getOtpExpiryTime() != null && user.getOtpExpiryTime().isBefore(Instant.now())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Mã OTP đã hết hạn, vui lòng yêu cầu mã mới");
        }

        // Kích hoạt tài khoản
        user.setActive(true);
        user.setEmailVerified(true);
        user.setOtp(null);
        user.setOtpExpiryTime(null);
        userRepository.save(user);

        try {
            redisTokenService.deleteOtp("verify-account:" + email);
        } catch (Exception ignored) {
        }

        log.info("Tài khoản {} đã được kích hoạt thành công qua mã OTP.", email);
        return true;
    }

    /**
     * 4. Chặn Đăng nhập (Login Block):
     * - Nếu isActive == false, ném Exception thông báo: "Tài khoản chưa được kích hoạt, vui lòng kiểm tra email"
     * - Nếu hợp lệ, cấp Access Token và Refresh Token
     */
    @Override
    public TokenResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // 1. Kiểm tra tài khoản trong database
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_CREDENTIALS));

        // 2. Chặn đăng nhập nếu chưa kích hoạt
        if (!user.isActive()) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Tài khoản chưa được kích hoạt, vui lòng kiểm tra email");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            return buildAndSaveTokens(userDetails);
        } catch (DisabledException ex) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Tài khoản chưa được kích hoạt, vui lòng kiểm tra email");
        } catch (BadCredentialsException ex) {
            throw new AppException(ErrorCode.BAD_CREDENTIALS);
        }
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

        if (!user.isActive()) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Tài khoản chưa được kích hoạt, vui lòng kiểm tra email");
        }

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
        }
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        String identity = request.getIdentity().trim().toLowerCase();
        User user = userRepository.findByEmail(identity).orElse(null);
        if (user == null) {
            throw new AppException(ErrorCode.BAD_CREDENTIALS, "Tài khoản không tồn tại trên hệ thống");
        }

        String otp = generateOtp();
        String redisKey = "forgot-password:" + identity;
        redisTokenService.saveOtp(redisKey, otp, 300);

        emailService.sendHtmlEmail(
                user.getEmail(),
                "[Ecommerce] Mã OTP đặt lại mật khẩu",
                "<p>Mã OTP đặt lại mật khẩu của bạn là: <strong>" + otp + "</strong> (hiệu lực 5 phút).</p>"
        );
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        String identity = request.getIdentity().trim().toLowerCase();
        String redisKey = "forgot-password:" + identity;
        String storedOtp = redisTokenService.getOtp(redisKey);

        if (storedOtp == null || !storedOtp.equals(request.getOtp().trim())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Mã OTP không chính xác hoặc đã hết hạn");
        }

        User user = userRepository.findByEmail(identity)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_CREDENTIALS, "Tài khoản không tồn tại"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        redisTokenService.deleteOtp(redisKey);
    }

    @Override
    public void sendVerificationOtp(String userId, SendOtpRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        String otp = generateOtp();
        user.setOtp(otp);
        user.setOtpExpiryTime(Instant.now().plus(5, ChronoUnit.MINUTES));
        userRepository.save(user);

        redisTokenService.saveOtp("verify-account:" + user.getEmail(), otp, 300);
        emailService.sendOtpEmail(user.getEmail(), otp);
    }

    @Override
    public void verifyAccount(String userId, VerifyAccountRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        String redisKey = "verify-account:" + user.getEmail();
        String storedOtp = redisTokenService.getOtp(redisKey);

        boolean match = (storedOtp != null && storedOtp.equals(request.getOtp().trim()))
                || (user.getOtp() != null && user.getOtp().equals(request.getOtp().trim()));

        if (!match) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Mã OTP xác minh không chính xác hoặc đã hết hạn");
        }

        user.setActive(true);
        user.setEmailVerified(true);
        user.setOtp(null);
        user.setOtpExpiryTime(null);
        userRepository.save(user);

        redisTokenService.deleteOtp(redisKey);
    }

    private String generateOtp() {
        int code = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(code);
    }

    private TokenResponse buildAndSaveTokens(CustomUserDetails userDetails) {
        String accessToken = jwtUtil.generateAccessToken(userDetails);
        String tokenId = UUID.randomUUID().toString();
        String refreshToken = jwtUtil.generateRefreshToken(userDetails, tokenId);

        redisTokenService.saveRefreshToken(
                userDetails.getId(), tokenId, appProperties.getJwt().getRefreshExpirationMs());

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(appProperties.getJwt().getAccessExpirationMs() / 1000)
                .build();
    }
}

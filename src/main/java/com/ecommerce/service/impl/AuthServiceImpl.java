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
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.HexFormat;
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
    private final Environment environment;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final int MAX_OTP_ATTEMPTS = 5;

    /**
     * 1. Đăng ký tài khoản:
     * - Lưu thông tin User với isActive = false
     * - Sinh mã OTP 6 số ngẫu nhiên thời hạn 5 phút, lưu dạng HASH vào DB và Redis
     * - Gửi email chứa mã OTP gốc qua JavaMailSender (chỉ log fallback mã OTP khi profile dev)
     */
    @Override
    public void register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        // Sinh mã OTP 6 số ngẫu nhiên
        String rawOtp = generateOtp();
        String hashedOtp = hashOtp(rawOtp);
        Instant otpExpiryTime = Instant.now().plus(5, ChronoUnit.MINUTES);

        // Lưu vào Database với isActive = false và OTP dạng hash
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .roles(Set.of(RoleEnum.ROLE_CUSTOMER))
                .active(false) // Trạng thái chưa kích hoạt
                .emailVerified(false)
                .phoneVerified(false)
                .otp(hashedOtp)
                .otpExpiryTime(otpExpiryTime)
                .build();

        userRepository.save(user);

        // Lưu cache OTP (dạng hash) vào Redis với thời hạn 300s (5 phút)
        try {
            redisTokenService.saveOtp("verify-account:" + email, hashedOtp, 300);
            redisTokenService.resetOtpAttempts("verify-account:" + email);
        } catch (Exception ex) {
            log.warn("Không thể lưu OTP vào Redis: {}", ex.getMessage());
        }

        // 2. Gửi email chứa mã OTP gốc
        try {
            emailService.sendOtpEmail(email, rawOtp);
            log.info("Đã tạo tài khoản cho {} và gửi OTP xác thực thành công.", email);
        } catch (Exception ex) {
            if (isDevProfile()) {
                log.warn("Không thể gửi email qua SMTP: {}. => [DEV OTP FALLBACK] Mã OTP cho email {}: [{}]",
                        ex.getMessage(), email, rawOtp);
            } else {
                log.warn("Không thể gửi email xác minh đến {} qua SMTP. Vui lòng kiểm tra Mail Server.", email);
            }
        }
    }

    /**
     * 3. Xác thực Email bằng mã OTP:
     * - Kiểm tra số lần nhập sai (tối đa 5 lần)
     * - So sánh constant-time hash OTP
     * - Cập nhật isActive = true, emailVerified = true
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

        String inputOtp = request.getOtp() != null ? request.getOtp().trim() : "";
        String inputHash = hashOtp(inputOtp);
        String redisKey = "verify-account:" + email;

        // Lấy hash OTP từ DB hoặc Redis
        String storedHash = user.getOtp();
        if (!StringUtils.hasText(storedHash)) {
            storedHash = redisTokenService.getOtp(redisKey);
        }

        boolean otpMatches = isConstantTimeEqual(inputHash, storedHash);

        if (!otpMatches) {
            int attempts = redisTokenService.incrementOtpAttempts("verify-account:" + email, 300);
            if (attempts >= MAX_OTP_ATTEMPTS) {
                // Hủy OTP khi vượt quá 5 lần nhập sai
                user.setOtp(null);
                user.setOtpExpiryTime(null);
                userRepository.save(user);
                redisTokenService.deleteOtp(redisKey);
                redisTokenService.resetOtpAttempts("verify-account:" + email);
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Bạn đã nhập sai mã OTP quá 5 lần. Mã OTP đã bị hủy để đảm bảo an toàn, vui lòng yêu cầu mã mới.");
            }
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Mã OTP không chính xác hoặc đã hết hạn (Lần thử " + attempts + "/" + MAX_OTP_ATTEMPTS + ")");
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

        redisTokenService.deleteOtp(redisKey);
        redisTokenService.resetOtpAttempts("verify-account:" + email);

        log.info("Tài khoản {} đã được kích hoạt thành công qua mã OTP.", email);
        return true;
    }

    /**
     * 4. Chặn Đăng nhập (Login Block):
     * - Nếu isActive == false và chưa xác thực email: ném ACCOUNT_NOT_ACTIVATED (yêu cầu verify OTP)
     * - Nếu isActive == false nhưng đã xác thực email: ném ACCESS_DENIED (bị admin khóa)
     * - Nếu hợp lệ, cấp Access Token và Refresh Token
     */
    @Override
    public TokenResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // 1. Kiểm tra tài khoản trong database
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_CREDENTIALS));

        // 2. Chặn đăng nhập nếu chưa kích hoạt hoặc bị khóa
        if (!user.isActive()) {
            if (!user.isEmailVerified()) {
                throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVATED, "Tài khoản chưa được kích hoạt, vui lòng kiểm tra email");
            } else {
                throw new AppException(ErrorCode.ACCESS_DENIED, "Tài khoản của bạn đã bị khóa bởi quản trị viên");
            }
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            return buildAndSaveTokens(userDetails);
        } catch (DisabledException ex) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVATED, "Tài khoản chưa được kích hoạt, vui lòng kiểm tra email");
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
            if (!user.isEmailVerified()) {
                throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVATED, "Tài khoản chưa được kích hoạt, vui lòng kiểm tra email");
            } else {
                throw new AppException(ErrorCode.ACCESS_DENIED, "Tài khoản của bạn đã bị khóa bởi quản trị viên");
            }
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
            String userId = claims.get(JwtUtil.CLAIM_USER_ID, String.class);

            // Thu hồi toàn bộ refresh token của người dùng khi đăng xuất
            if (StringUtils.hasText(userId)) {
                redisTokenService.deleteAllRefreshTokensForUser(userId);
            }

            if (remainingTtl > 0) {
                redisTokenService.blacklistAccessToken(accessToken, remainingTtl);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Quên mật khẩu: Trả phản hồi an toàn giống nhau dù email có tồn tại hay không (chống Email Enumeration)
     */
    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        String identity = request.getIdentity().trim().toLowerCase();
        User user = userRepository.findByEmail(identity).orElse(null);
        if (user == null) {
            log.info("Quên mật khẩu: Email không tồn tại trên hệ thống [{}], trả phản hồi đồng nhất an toàn.", identity);
            return;
        }

        String rawOtp = generateOtp();
        String hashedOtp = hashOtp(rawOtp);
        String redisKey = "forgot-password:" + identity;

        redisTokenService.saveOtp(redisKey, hashedOtp, 300);
        redisTokenService.resetOtpAttempts("forgot-password:" + identity);

        try {
            emailService.sendHtmlEmail(
                    user.getEmail(),
                    "[Ecommerce] Mã OTP đặt lại mật khẩu",
                    "<p>Mã OTP đặt lại mật khẩu của bạn là: <strong>" + rawOtp + "</strong> (hiệu lực 5 phút).</p>"
            );
        } catch (Exception ex) {
            if (isDevProfile()) {
                log.warn("[DEV OTP FALLBACK] Mã OTP quên mật khẩu cho email {}: [{}]", identity, rawOtp);
            } else {
                log.warn("Không thể gửi email OTP quên mật khẩu đến {}.", identity);
            }
        }
    }

    /**
     * Đặt lại mật khẩu:
     * - Kiểm tra số lần thử tối đa 5 lần
     * - So sánh constant-time
     * - Thu hồi toàn bộ Refresh Token của user sau khi đổi mật khẩu thành công
     */
    @Override
    public void resetPassword(ResetPasswordRequest request) {
        String identity = request.getIdentity().trim().toLowerCase();
        String redisKey = "forgot-password:" + identity;
        String storedHash = redisTokenService.getOtp(redisKey);

        String inputOtp = request.getOtp() != null ? request.getOtp().trim() : "";
        String inputHash = hashOtp(inputOtp);

        boolean match = isConstantTimeEqual(inputHash, storedHash);

        if (!match) {
            int attempts = redisTokenService.incrementOtpAttempts("forgot-password:" + identity, 300);
            if (attempts >= MAX_OTP_ATTEMPTS) {
                redisTokenService.deleteOtp(redisKey);
                redisTokenService.resetOtpAttempts("forgot-password:" + identity);
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Bạn đã nhập sai mã OTP quá 5 lần. Mã OTP đã bị hủy để đảm bảo an toàn, vui lòng yêu cầu mã mới.");
            }
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Mã OTP không chính xác hoặc đã hết hạn (Lần thử " + attempts + "/" + MAX_OTP_ATTEMPTS + ")");
        }

        User user = userRepository.findByEmail(identity)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_CREDENTIALS, "Tài khoản không tồn tại"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        redisTokenService.deleteOtp(redisKey);
        redisTokenService.resetOtpAttempts("forgot-password:" + identity);

        // Thu hồi toàn bộ refresh token sau khi reset mật khẩu
        redisTokenService.deleteAllRefreshTokensForUser(user.getId());
        log.info("Đặt lại mật khẩu thành công cho {}. Đã thu hồi toàn bộ phiên đăng nhập cũ.", identity);
    }

    @Override
    public void sendVerificationOtp(String userId, SendOtpRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        String rawOtp = generateOtp();
        String hashedOtp = hashOtp(rawOtp);
        user.setOtp(hashedOtp);
        user.setOtpExpiryTime(Instant.now().plus(5, ChronoUnit.MINUTES));
        userRepository.save(user);

        redisTokenService.saveOtp("verify-account:" + user.getEmail(), hashedOtp, 300);
        redisTokenService.resetOtpAttempts("verify-account:" + user.getEmail());

        try {
            emailService.sendOtpEmail(user.getEmail(), rawOtp);
        } catch (Exception ex) {
            if (isDevProfile()) {
                log.warn("[DEV OTP FALLBACK] Mã OTP gửi lại cho email {}: [{}]", user.getEmail(), rawOtp);
            } else {
                log.warn("Không thể gửi email xác minh qua SMTP cho {}.", user.getEmail());
            }
        }
    }

    @Override
    public void verifyAccount(String userId, VerifyAccountRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        String redisKey = "verify-account:" + user.getEmail();
        String storedHash = redisTokenService.getOtp(redisKey);
        if (!StringUtils.hasText(storedHash)) {
            storedHash = user.getOtp();
        }

        String inputOtp = request.getOtp() != null ? request.getOtp().trim() : "";
        String inputHash = hashOtp(inputOtp);

        boolean match = isConstantTimeEqual(inputHash, storedHash);

        if (!match) {
            int attempts = redisTokenService.incrementOtpAttempts("verify-account:" + user.getEmail(), 300);
            if (attempts >= MAX_OTP_ATTEMPTS) {
                user.setOtp(null);
                user.setOtpExpiryTime(null);
                userRepository.save(user);
                redisTokenService.deleteOtp(redisKey);
                redisTokenService.resetOtpAttempts("verify-account:" + user.getEmail());
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Bạn đã nhập sai mã OTP quá 5 lần. Mã OTP đã bị hủy để đảm bảo an toàn, vui lòng yêu cầu mã mới.");
            }
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Mã OTP xác minh không chính xác hoặc đã hết hạn (Lần thử " + attempts + "/" + MAX_OTP_ATTEMPTS + ")");
        }

        user.setActive(true);
        user.setEmailVerified(true);
        user.setOtp(null);
        user.setOtpExpiryTime(null);
        userRepository.save(user);

        redisTokenService.deleteOtp(redisKey);
        redisTokenService.resetOtpAttempts("verify-account:" + user.getEmail());
    }

    private String generateOtp() {
        int code = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(code);
    }

    private String hashOtp(String otp) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(otp.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi băm OTP", e);
        }
    }

    private boolean isConstantTimeEqual(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isDevProfile() {
        if (environment == null) {
            return false;
        }
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
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

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
import com.ecommerce.service.OtpThrottleService;
import com.ecommerce.service.PasswordPolicyService;
import com.ecommerce.service.RedisTokenService;
import com.ecommerce.dto.response.VerifyResetOtpResponse;
import com.ecommerce.util.DataMaskingUtil;
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
    private final OtpThrottleService otpThrottleService;
    private final PasswordPolicyService passwordPolicyService;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final int MAX_LOGIN_FAILS_PER_IP = 5;
    private static final int MAX_LOGIN_FAILS_PER_EMAIL = 20;
    private static final long LOGIN_LOCKOUT_DURATION_SECONDS = 900; // 15 phút
    private static final String DUMMY_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String DUMMY_BCRYPT_HASH = "$2a$12$e8k8E5.O3mE0aWwX8H1s2u8yq1P.u.U/bOq9f3YhXqYj1H6V5Yh1q";

    public static String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase(java.util.Locale.ROOT) : "";
    }

    /**
     * 1. Đăng ký tài khoản:
     * - Tuân thủ cooldown và trần gửi OTP (T4)
     * - Kiểm tra chính sách mật khẩu và dữ liệu (T7)
     * - Nếu email thuộc user chưa kích hoạt và chưa verify: cập nhật thông tin và cấp OTP mới (T2)
     * - Bắt DuplicateKeyException khi đăng ký đồng thời (T2)
     */
    @Override
    public void register(RegisterRequest request) {
        String email = passwordPolicyService.normalizeEmail(request.getEmail());
        passwordPolicyService.validateFullName(request.getFullName());
        passwordPolicyService.validatePhone(request.getPhone());
        passwordPolicyService.validatePassword(request.getPassword(), email);

        // Kiểm tra cooldown và giới hạn gửi OTP theo email (T4)
        otpThrottleService.checkAndRecordOtpRequest(email, "verify-account");

        User existingUser = userRepository.findByEmail(email).orElse(null);
        if (existingUser != null) {
            if (existingUser.isEmailVerified()) {
                throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }

            // N1.a: Tài khoản chưa kích hoạt và chưa verify: KHÔNG ghi đè fullName/phone/password/createdAt
            // Chỉ cấp OTP mới (qua throttle) và trả cùng phản hồi
            String rawOtp = generateOtp();
            String hashedOtp = hashOtp(rawOtp);
            Instant otpExpiryTime = Instant.now().plus(5, ChronoUnit.MINUTES);

            existingUser.setOtp(hashedOtp);
            existingUser.setOtpExpiryTime(otpExpiryTime);
            userRepository.save(existingUser);

            redisTokenService.saveOtp("verify-account:" + email, hashedOtp, 300);
            redisTokenService.resetOtpAttempts("verify-account:" + email);

            try {
                emailService.sendOtpEmail(email, rawOtp);
                log.info("Đã cập nhật thông tin và gửi lại OTP xác thực cho tài khoản chưa kích hoạt: {}", DataMaskingUtil.maskEmail(email));
            } catch (Exception ex) {
                if (isDevProfile()) {
                    log.warn("Không thể gửi email qua SMTP: {}. => [DEV OTP FALLBACK] Mã OTP cho email {}: [{}]",
                            ex.getMessage(), email, rawOtp);
                } else {
                    log.error("Không thể gửi email xác minh đến {} qua SMTP.", DataMaskingUtil.maskEmail(email), ex);
                }
            }
            return;
        }

        // Sinh mã OTP 6 số ngẫu nhiên
        String rawOtp = generateOtp();
        String hashedOtp = hashOtp(rawOtp);
        Instant otpExpiryTime = Instant.now().plus(5, ChronoUnit.MINUTES);

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .roles(Set.of(RoleEnum.ROLE_CUSTOMER))
                .active(false)
                .emailVerified(false)
                .phoneVerified(false)
                .otp(hashedOtp)
                .otpExpiryTime(otpExpiryTime)
                .createdAt(Instant.now())
                .build();

        try {
            userRepository.save(user);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            log.warn("Xảy ra va chạm unique key khi đăng ký đồng thời email {}: {}", DataMaskingUtil.maskEmail(email), ex.getMessage());
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        redisTokenService.saveOtp("verify-account:" + email, hashedOtp, 300);
        redisTokenService.resetOtpAttempts("verify-account:" + email);

        try {
            emailService.sendOtpEmail(email, rawOtp);
            log.info("Đã tạo tài khoản cho {} và gửi OTP xác thực thành công.", DataMaskingUtil.maskEmail(email));
        } catch (Exception ex) {
            if (isDevProfile()) {
                log.warn("Không thể gửi email qua SMTP: {}. => [DEV OTP FALLBACK] Mã OTP cho email {}: [{}]",
                        ex.getMessage(), email, rawOtp);
            } else {
                log.error("Không thể gửi email xác minh đến {} qua SMTP.", email, ex);
            }
        }
    }

    /**
     * 2. Gửi lại mã OTP xác thực (Public - T1):
     * - Trả cùng thông điệp thành công dù email tồn tại hay không
     * - Chỉ gửi khi user tồn tại, active=false và emailVerified=false
     */
    @Override
    public void resendVerificationOtp(ResendOtpRequest request) {
        String email = normalizeEmail(request.getEmail());

        // Áp dụng throttle theo email bất kể tồn tại hay không (T4)
        otpThrottleService.checkAndRecordOtpRequest(email, "verify-account");

        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null && !user.isActive() && !user.isEmailVerified()) {
            String rawOtp = generateOtp();
            String hashedOtp = hashOtp(rawOtp);
            Instant otpExpiryTime = Instant.now().plus(5, ChronoUnit.MINUTES);

            user.setOtp(hashedOtp);
            user.setOtpExpiryTime(otpExpiryTime);
            userRepository.save(user);

            redisTokenService.saveOtp("verify-account:" + email, hashedOtp, 300);
            redisTokenService.resetOtpAttempts("verify-account:" + email);

            try {
                emailService.sendOtpEmail(email, rawOtp);
                log.info("Đã gửi lại mã OTP xác thực cho tài khoản chưa kích hoạt: {}", DataMaskingUtil.maskEmail(email));
            } catch (Exception ex) {
                if (isDevProfile()) {
                    log.warn("[DEV OTP FALLBACK] Mã OTP gửi lại cho email {}: [{}]", email, rawOtp);
                } else {
                    log.error("Không thể gửi email xác minh đến {} qua SMTP.", email, ex);
                }
            }
        } else {
            log.info("Gửi lại OTP: Email [{}] không thỏa điều kiện gửi lại (không tồn tại, đã verify, hoặc bị khóa).", DataMaskingUtil.maskEmail(email));
        }
    }

    /**
     * 3. Xác thực Email bằng mã OTP (T5):
     * - Fail-closed qua RedisTokenService
     * - Kiểm tra số lần đoán sai theo giờ (khóa 1 giờ nếu >= 10 lần sai)
     * - Trả cùng lỗi cho tài khoản không tồn tại, đã active hoặc sai OTP (không lộ trạng thái)
     */
    @Override
    public boolean verifyOtp(VerifyOtpRequest request) {
        String email = normalizeEmail(request.getTargetEmail());
        if (!StringUtils.hasText(email)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Vui lòng cung cấp email cần xác thực");
        }

        try {
            // 1. Kiểm tra bộ đếm sai theo giờ (T5)
            int hourlyFailed = redisTokenService.getHourlyFailedAttempts("verify-account", email);
            if (hourlyFailed >= 10) {
                throw new AppException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED,
                        "Bạn đã nhập sai mã OTP hoặc mật khẩu quá 10 lần trong 1 giờ. Luồng OTP tạm thời bị khóa 1 giờ.");
            }

            User user = userRepository.findByEmail(email).orElse(null);
            boolean eligible = (user != null && !user.isActive() && !user.isEmailVerified());

            String inputOtp = request.getOtp() != null ? request.getOtp().trim() : "";
            String inputHash = hashOtp(inputOtp);
            String redisKey = "verify-account:" + email;

            String storedHash;
            String userPasswordHash;
            if (eligible) {
                storedHash = redisTokenService.getOtp(redisKey);
                if (!StringUtils.hasText(storedHash)) {
                    storedHash = user.getOtp();
                }
                userPasswordHash = user.getPassword();
            } else {
                // Giả lập stored hash và password hash để thời gian tính toán tương đương
                storedHash = DUMMY_HASH;
                userPasswordHash = DUMMY_BCRYPT_HASH;
            }

            String inputPassword = request.getPassword() != null ? request.getPassword() : "";
            boolean otpMatches = isConstantTimeEqual(inputHash, storedHash != null ? storedHash : DUMMY_HASH);
            boolean passwordMatches = passwordEncoder.matches(inputPassword, userPasswordHash != null ? userPasswordHash : DUMMY_BCRYPT_HASH);

            // N1.b: Tính cả hai điều kiện, không short-circuit
            boolean valid = eligible & otpMatches & passwordMatches;

            if (!valid) {
                // Tăng bộ đếm sai theo giờ cho email (không bị reset khi xin OTP mới)
                redisTokenService.incrementHourlyFailedAttempts("verify-account", email, 3600);

                int attempts = redisTokenService.incrementOtpAttempts("verify-account:" + email, 300);
                if (attempts >= MAX_OTP_ATTEMPTS) {
                    if (user != null) {
                        user.setOtp(null);
                        user.setOtpExpiryTime(null);
                        userRepository.save(user);
                    }
                    redisTokenService.deleteOtp(redisKey);
                    redisTokenService.resetOtpAttempts("verify-account:" + email);
                    throw new AppException(ErrorCode.VALIDATION_ERROR,
                            "Bạn đã nhập sai thông tin xác thực quá 5 lần. Mã OTP đã bị hủy để đảm bảo an toàn, vui lòng yêu cầu mã mới.");
                }
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Mã OTP hoặc mật khẩu không chính xác hoặc đã hết hạn (Lần thử " + attempts + "/" + MAX_OTP_ATTEMPTS + ")");
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

            log.info("Tài khoản {} đã được kích hoạt thành công qua mã OTP và mật khẩu.", DataMaskingUtil.maskEmail(email));
            return true;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi khi xác thực OTP trên Redis (fail-closed): {}", e.getMessage());
            throw new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE, "Dịch vụ xác thực tạm thời không khả dụng, vui lòng thử lại sau.");
        }
    }

    /**
     * 4. Đăng nhập (T13):
     * - Kiểm tra khóa tạm tài khoản bằng Redis (5 lần/IP hoặc 20 lần/email trong 15 phút)
     * - Kiểm tra mật khẩu TRƯỚC (chống lộ trạng thái tài khoản khi chưa đúng mật khẩu)
     * - Nếu email không tồn tại, thực thi matches với hash giả để thời gian tương đương
     * - Chỉ thông báo chưa kích hoạt hoặc bị khóa sau khi mật khẩu đã đúng
     */
    @Override
    public TokenResponse login(LoginRequest request) {
        return login(request, "unknown");
    }

    @Override
    public TokenResponse login(LoginRequest request, String clientIp) {
        String email = passwordPolicyService.normalizeEmail(request.getEmail());

        // 1. Kiểm tra khóa tạm tài khoản (T13)
        int failsIp = redisTokenService.getLoginFailAttemptsByIp(email, clientIp);
        int failsEmail = redisTokenService.getLoginFailAttemptsByEmail(email);
        if (failsIp >= MAX_LOGIN_FAILS_PER_IP || failsEmail >= MAX_LOGIN_FAILS_PER_EMAIL) {
            log.warn("Đăng nhập bị khóa tạm thời cho email [{}] từ IP [{}]: failsIp={}, failsEmail={}",
                    DataMaskingUtil.maskEmail(email), clientIp, failsIp, failsEmail);
            throw new AppException(ErrorCode.ACCOUNT_TEMP_LOCKED,
                    "Tài khoản hoặc thiết bị tạm thời bị khóa do nhập sai mật khẩu nhiều lần. Vui lòng thử lại sau 15 phút.");
        }

        // 2. Tìm tài khoản trong database
        User user = userRepository.findByEmail(email).orElse(null);

        // 3. Kiểm tra mật khẩu TRƯỚC (chống rà quét tài khoản - V6 & V11)
        boolean passwordMatches;
        String rawPassword = request.getPassword() != null ? request.getPassword() : "";

        if (user != null) {
            passwordMatches = passwordEncoder.matches(rawPassword, user.getPassword());
        } else {
            // Chạy matches với hash giả để thời gian xử lý đồng nhất với tài khoản tồn tại
            passwordEncoder.matches(rawPassword, DUMMY_BCRYPT_HASH);
            passwordMatches = false;
        }

        if (!passwordMatches) {
            redisTokenService.recordLoginFailure(email, clientIp, LOGIN_LOCKOUT_DURATION_SECONDS);
            log.warn("Đăng nhập thất bại: Sai email hoặc mật khẩu cho [{}] từ IP [{}]", DataMaskingUtil.maskEmail(email), clientIp);
            throw new AppException(ErrorCode.BAD_CREDENTIALS);
        }

        // 4. Mật khẩu đúng: Reset bộ đếm thử sai của email + IP
        redisTokenService.resetLoginFailure(email, clientIp);

        // 5. Kiểm tra trạng thái tài khoản sau khi mật khẩu đã đúng
        if (!user.isActive()) {
            if (!user.isEmailVerified()) {
                throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVATED, "Tài khoản chưa được kích hoạt, vui lòng kiểm tra email");
            } else {
                throw new AppException(ErrorCode.ACCESS_DENIED, "Tài khoản của bạn đã bị khóa bởi quản trị viên");
            }
        }

        CustomUserDetails userDetails = new CustomUserDetails(user);
        return buildAndSaveTokens(userDetails);
    }

    @Override
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        Claims claims = jwtUtil.verifyRefreshToken(request.getRefreshToken());
        String userId = claims.get(JwtUtil.CLAIM_USER_ID, String.class);
        String tokenId = claims.getId();

        Date issuedAt = claims.getIssuedAt();
        long iatSeconds = (issuedAt != null) ? (issuedAt.getTime() / 1000) : 0;

        // N3.d: Kiểm tra mốc khóa theo iat của refresh token bằng biến thể fail-closed
        if (redisTokenService.isUserBlockedFailClosed(userId, iatSeconds)) {
            log.warn("Refresh token bị từ chối do phát hành trước mốc khóa/thu hồi phiên (userId={}, iat={})",
                    userId, iatSeconds);
            throw new AppException(ErrorCode.TOKEN_REVOKED, "Phiên đăng nhập đã bị thu hồi, vui lòng đăng nhập lại");
        }

        // T12: Phát hiện dùng lại token cũ (Replay / Theft detection)
        if (redisTokenService.isRefreshTokenUsed(userId, tokenId)) {
            log.warn("CẢNH BÁO AN NINH: Phát hiện refresh token đã dùng trước đó bị sử dụng lại (userId={}, jti={}). Khởi động cơ chế thu hồi toàn bộ phiên!",
                    userId, tokenId);
            redisTokenService.revokeAllSessions(userId);
            throw new AppException(ErrorCode.TOKEN_REVOKED, "Phiên đăng nhập đã bị thu hồi do phát hiện token bị tái sử dụng");
        }

        // T12: Thu hồi atomic - chỉ request xóa thành công mới được đi tiếp
        boolean deleted = redisTokenService.deleteRefreshTokenAtomic(userId, tokenId);
        if (!deleted) {
            throw new AppException(ErrorCode.TOKEN_REVOKED);
        }

        // Đánh dấu token đã dùng với TTL bằng thời hạn còn lại
        Date expiration = claims.getExpiration();
        long remainingTtl = (expiration != null) ? (expiration.getTime() - System.currentTimeMillis()) : 0;
        if (remainingTtl > 0) {
            redisTokenService.markRefreshTokenAsUsed(userId, tokenId, remainingTtl);
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

        CustomUserDetails userDetails = new CustomUserDetails(user);
        return buildAndSaveTokens(userDetails);
    }

    @Override
    public void logout(String accessToken) {
        logout(accessToken, null);
    }

    @Override
    public void logout(String accessToken, LogoutRequest request) {
        String userIdFromAccess = null;
        if (StringUtils.hasText(accessToken)) {
            try {
                // T11: Chấp nhận access token đã hết hạn
                Claims claims = jwtUtil.parseAccessTokenAllowExpired(accessToken);
                userIdFromAccess = claims.get(JwtUtil.CLAIM_USER_ID, String.class);
                Date expiration = claims.getExpiration();
                long remainingTtl = (expiration != null) ? (expiration.getTime() - System.currentTimeMillis()) : 0;
                if (remainingTtl > 0) {
                    redisTokenService.blacklistAccessToken(accessToken, remainingTtl);
                }
            } catch (Exception ex) {
                log.warn("Không thể parse access token khi logout: {}", ex.getMessage());
            }
        }

        // Nếu client cung cấp refreshToken trong body: chỉ thu hồi đúng refresh token đó
        if (request != null && StringUtils.hasText(request.getRefreshToken())) {
            try {
                Claims refreshClaims = jwtUtil.verifyRefreshToken(request.getRefreshToken());
                String refreshUserId = refreshClaims.get(JwtUtil.CLAIM_USER_ID, String.class);
                String refreshJti = refreshClaims.getId();
                if (StringUtils.hasText(refreshUserId) && StringUtils.hasText(refreshJti)) {
                    redisTokenService.deleteRefreshToken(refreshUserId, refreshJti);
                    log.info("Đã thu hồi đúng refresh token (jti={}) của user {} khi đăng xuất.", refreshJti, refreshUserId);
                    return;
                }
            } catch (Exception ex) {
                log.warn("Không thể verify refresh token khi logout: {}", ex.getMessage());
            }
        }

        // Không có refreshToken trong body: fallback hành vi cũ (thu hồi tất cả)
        if (StringUtils.hasText(userIdFromAccess)) {
            redisTokenService.deleteAllRefreshTokensForUser(userIdFromAccess);
            log.info("Thu hồi toàn bộ phiên đăng nhập của user {} do không cung cấp refreshToken cụ thể (hành vi cũ).", userIdFromAccess);
        }
    }

    @Override
    public void logoutAll(String userId) {
        redisTokenService.revokeAllSessions(userId);
        log.info("Đã đăng xuất toàn bộ thiết bị (revokeAllSessions) cho user {}", userId);
    }

    /**
     * 6. Quên mật khẩu - Bước 1: Gửi mã OTP xác nhận về Email (Cách B - 2 bước)
     * - Áp dụng throttle theo email
     * - Thông điệp phản hồi chung, không lộ thông tin
     * - Gửi email bất đồng bộ
     */
    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        String identity = passwordPolicyService.normalizeEmail(request.getEmail());

        // Áp dụng throttle (cooldown 60s, tối đa 5 OTP/giờ) bất kể email tồn tại hay không
        otpThrottleService.checkAndRecordOtpRequest(identity, "forgot-password");

        User user = userRepository.findByEmail(identity).orElse(null);
        if (user == null) {
            log.info("Quên mật khẩu: Email không tồn tại trên hệ thống [{}], trả phản hồi đồng nhất an toàn.", DataMaskingUtil.maskEmail(identity));
            return;
        }

        String rawOtp = generateOtp();
        String hashedOtp = hashOtp(rawOtp);
        String redisKey = "forgot-password:" + identity;

        redisTokenService.saveOtp(redisKey, hashedOtp, 300);
        redisTokenService.resetOtpAttempts(redisKey);

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
                log.error("Không thể gửi email OTP quên mật khẩu đến {}.", DataMaskingUtil.maskEmail(identity), ex);
            }
        }
    }

    /**
     * 6b. Xác thực OTP quên mật khẩu - Bước 2a (Cách B):
     * - Kiểm tra số lần đoán sai theo giờ (khóa 1 giờ nếu >= 10 lần sai)
     * - Kiểm tra OTP (hash, constant-time, giới hạn 5 lần)
     * - Đúng thì xóa OTP và sinh resetToken (32 bytes Base64Url), lưu hash SHA-256 vào Redis (TTL 10 phút)
     * - Mỗi user chỉ có 1 token hiệu lực
     */
    @Override
    public VerifyResetOtpResponse verifyResetOtp(VerifyResetOtpRequest request) {
        String identity = passwordPolicyService.normalizeEmail(request.getIdentity());

        // Kiểm tra bộ đếm sai theo giờ
        int hourlyFailed = redisTokenService.getHourlyFailedAttempts("forgot-password", identity);
        if (hourlyFailed >= 10) {
            throw new AppException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED,
                    "Bạn đã nhập sai mã OTP quá 10 lần trong 1 giờ. Luồng OTP tạm thời bị khóa 1 giờ.");
        }

        User user = userRepository.findByEmail(identity).orElse(null);
        String redisKey = "forgot-password:" + identity;

        String storedHash;
        if (user != null) {
            storedHash = redisTokenService.getOtp(redisKey);
        } else {
            storedHash = DUMMY_HASH;
        }

        String inputOtp = request.getOtp() != null ? request.getOtp().trim() : "";
        String inputHash = hashOtp(inputOtp);

        boolean match = (user != null) && isConstantTimeEqual(inputHash, storedHash);

        if (!match) {
            redisTokenService.incrementHourlyFailedAttempts("forgot-password", identity, 3600);
            int attempts = redisTokenService.incrementOtpAttempts(redisKey, 300);
            if (attempts >= MAX_OTP_ATTEMPTS) {
                redisTokenService.deleteOtp(redisKey);
                redisTokenService.resetOtpAttempts(redisKey);
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Bạn đã nhập sai mã OTP quá 5 lần. Mã OTP đã bị hủy để đảm bảo an toàn, vui lòng yêu cầu mã mới.");
            }
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Mã OTP không chính xác hoặc đã hết hạn (Lần thử " + attempts + "/" + MAX_OTP_ATTEMPTS + ")");
        }

        // Khớp OTP: Xóa OTP trong Redis
        redisTokenService.deleteOtp(redisKey);
        redisTokenService.resetOtpAttempts(redisKey);

        // Sinh resetToken 32 bytes ngẫu nhiên
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String resetToken = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = hashOtp(resetToken);
        long expiresInSeconds = 600; // 10 phút

        redisTokenService.savePasswordResetToken(user.getId(), tokenHash, expiresInSeconds);

        log.info("Xác thực OTP quên mật khẩu thành công cho {}. Đã cấp resetToken.", DataMaskingUtil.maskEmail(identity));
        return VerifyResetOtpResponse.builder()
                .resetToken(resetToken)
                .expiresIn(expiresInSeconds)
                .build();
    }

    /**
     * 7. Đặt lại mật khẩu mới bằng resetToken - Bước 2b (Cách B):
     * - Tiêu thụ resetToken ATOMIC (GETDEL)
     * - Kiểm tra chính sách mật khẩu (T7)
     * - Cập nhật mật khẩu trong DB
     * - Thu hồi mọi phiên cũ (T10)
     * - Gửi email thông báo đổi mật khẩu (T8)
     */
    @Override
    public void resetPassword(ResetPasswordRequest request) {
        String rawToken = request.getResetToken() != null ? request.getResetToken().trim() : "";
        String tokenHash = hashOtp(rawToken);

        // N2: Peek token fail-closed mà KHÔNG tiêu thụ token
        String userId = redisTokenService.peekPasswordResetToken(tokenHash);
        if (!StringUtils.hasText(userId)) {
            throw new AppException(ErrorCode.RESET_TOKEN_INVALID,
                    "Mã xác thực đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESET_TOKEN_INVALID,
                        "Mã xác thực đặt lại mật khẩu không hợp lệ"));

        // N2: Kiểm tra chính sách mật khẩu TRƯỚC KHI tiêu thụ token
        passwordPolicyService.validatePassword(request.getNewPassword(), user.getEmail());

        // N2: Tiêu thụ token atomic (nếu null do chạy đua -> RESET_TOKEN_INVALID)
        String consumedUserId = redisTokenService.consumePasswordResetTokenAtomic(tokenHash);
        if (!StringUtils.hasText(consumedUserId)) {
            throw new AppException(ErrorCode.RESET_TOKEN_INVALID,
                    "Mã xác thực đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // N3.c: Thu hồi toàn bộ phiên đăng nhập cũ; nếu lỗi thì log ERROR và ném lỗi rõ ràng
        try {
            redisTokenService.revokeAllSessions(user.getId());
            log.info("Đặt lại mật khẩu thành công cho user {}. Đã thu hồi toàn bộ phiên đăng nhập cũ.", DataMaskingUtil.maskEmail(user.getEmail()));
        } catch (Exception ex) {
            log.error("Mật khẩu của user {} đã đổi nhưng lỗi khi thu hồi toàn bộ phiên đăng nhập: {}", DataMaskingUtil.maskEmail(user.getEmail()), ex.getMessage());
            throw new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE,
                    "mật khẩu đã đổi nhưng chưa đăng xuất được các thiết bị khác, hãy thử 'Đăng xuất tất cả'");
        }

        // Gửi email thông báo đổi mật khẩu
        try {
            emailService.sendPasswordChangedEmail(user.getEmail());
        } catch (Exception ex) {
            log.error("Không thể gửi email thông báo đổi mật khẩu cho {}: {}", DataMaskingUtil.maskEmail(user.getEmail()), ex.getMessage());
        }
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

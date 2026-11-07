package com.ecommerce.service;

import com.ecommerce.common.constant.RoleEnum;
import com.ecommerce.config.AppProperties;
import com.ecommerce.document.User;
import com.ecommerce.dto.request.*;
import com.ecommerce.dto.response.TokenResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.security.CustomUserDetails;
import com.ecommerce.service.impl.AuthServiceImpl;
import com.ecommerce.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthEmailOtpFlowTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RedisTokenService redisTokenService;

    @Mock
    private EmailService emailService;

    @Mock
    private Environment environment;

    @Mock
    private com.ecommerce.service.OtpThrottleService otpThrottleService;

    @Mock
    private com.ecommerce.service.PasswordPolicyService passwordPolicyService;

    private AppProperties appProperties;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getJwt().setAccessExpirationMs(3600000L);
        appProperties.getJwt().setRefreshExpirationMs(604800000L);

        lenient().when(passwordPolicyService.normalizeEmail(anyString()))
                .thenAnswer(inv -> {
                    String str = inv.getArgument(0);
                    return str != null ? str.trim().toLowerCase(java.util.Locale.ROOT) : "";
                });

        authService = new AuthServiceImpl(
                userRepository,
                passwordEncoder,
                authenticationManager,
                jwtUtil,
                redisTokenService,
                emailService,
                appProperties,
                environment,
                otpThrottleService,
                passwordPolicyService
        );
    }

    private String hashOtp(String otp) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(otp.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("1. Đăng ký tài khoản: Lưu user với isActive=false, OTP dạng hash SHA-256 và gửi email mã gốc")
    void register_Success_CreatesInactiveUserAndSendsEmailOtp() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Nguyen Van A");
        request.setEmail("nguyenvana@gmail.com");
        request.setPassword("password123");

        when(userRepository.findByEmail("nguyenvana@gmail.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encoded_pass");

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertNotNull(savedUser);
        assertEquals("nguyenvana@gmail.com", savedUser.getEmail());
        assertFalse(savedUser.isActive(), "Tài khoản mới phải có isActive = false");
        assertFalse(savedUser.isEmailVerified());
        assertNotNull(savedUser.getOtp(), "Mã OTP phải được lưu dạng hash");
        assertEquals(64, savedUser.getOtp().length(), "Mã OTP trong DB phải là chuỗi SHA-256 64 ký tự");
        assertNotNull(savedUser.getOtpExpiryTime());
        assertTrue(savedUser.getOtpExpiryTime().isAfter(Instant.now()), "OTP phải có thời hạn trong tương lai (5 phút)");

        // Kiểm tra gửi email mã OTP 6 số ngẫu nhiên
        verify(emailService, times(1)).sendOtpEmail(eq("nguyenvana@gmail.com"), anyString());
    }

    @Test
    @DisplayName("2. Chặn đăng nhập: Tài khoản isActive=false bị chặn với mã ACCOUNT_NOT_ACTIVATED")
    void login_InactiveUser_ThrowsException() {
        String email = "unverified@gmail.com";
        User inactiveUser = User.builder()
                .id("u1")
                .email(email)
                .password("encoded_pass")
                .active(false) // Chưa kích hoạt
                .emailVerified(false)
                .roles(Set.of(RoleEnum.ROLE_CUSTOMER))
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(inactiveUser));
        when(passwordEncoder.matches("password123", "encoded_pass")).thenReturn(true);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword("password123");

        AppException ex = assertThrows(AppException.class, () -> authService.login(loginRequest));
        assertEquals(ErrorCode.ACCOUNT_NOT_ACTIVATED, ex.getErrorCode());
        assertEquals("Tài khoản chưa được kích hoạt, vui lòng kiểm tra email", ex.getMessage());
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("3. Xác thực OTP: Mã đúng và còn hạn -> Cập nhật isActive=true")
    void verifyOtp_Success_ActivatesUser() {
        String email = "verify@gmail.com";
        String rawOtp = "123456";
        String hashedOtp = hashOtp(rawOtp);

        User user = User.builder()
                .id("u2")
                .email(email)
                .active(false)
                .otp(hashedOtp)
                .otpExpiryTime(Instant.now().plus(4, ChronoUnit.MINUTES))
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        VerifyOtpRequest request = VerifyOtpRequest.builder()
                .email(email)
                .otp(rawOtp)
                .build();

        boolean result = authService.verifyOtp(request);

        assertTrue(result);
        assertTrue(user.isActive(), "Tài khoản phải được chuyển sang isActive = true");
        assertTrue(user.isEmailVerified());
        assertNull(user.getOtp(), "Mã OTP phải bị xóa sau khi kích hoạt thành công");
        assertNull(user.getOtpExpiryTime());
        verify(userRepository, times(1)).save(user);
        verify(redisTokenService, times(1)).resetOtpAttempts("verify-account:" + email);
    }

    @Test
    @DisplayName("4. Xác thực OTP thất bại khi nhập sai OTP (dưới 5 lần)")
    void verifyOtp_InvalidOtp_IncrementsAttemptsAndThrowsException() {
        String email = "verify@gmail.com";
        String rawOtp = "123456";
        String hashedOtp = hashOtp(rawOtp);

        User user = User.builder()
                .id("u2")
                .email(email)
                .active(false)
                .otp(hashedOtp)
                .otpExpiryTime(Instant.now().plus(4, ChronoUnit.MINUTES))
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(redisTokenService.incrementOtpAttempts("verify-account:" + email, 300)).thenReturn(1);

        VerifyOtpRequest request = VerifyOtpRequest.builder()
                .email(email)
                .otp("000000") // Sai OTP
                .build();

        AppException ex = assertThrows(AppException.class, () -> authService.verifyOtp(request));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("1/5"));
        assertFalse(user.isActive());
    }

    @Test
    @DisplayName("5. Nhập sai OTP quá 5 lần: Mã OTP bị hủy ngay lập tức để bảo vệ brute-force")
    void verifyOtp_ExceedsMaxAttempts_CancelsOtp() {
        String email = "brute-force@gmail.com";
        String rawOtp = "123456";
        String hashedOtp = hashOtp(rawOtp);

        User user = User.builder()
                .id("u2")
                .email(email)
                .active(false)
                .otp(hashedOtp)
                .otpExpiryTime(Instant.now().plus(4, ChronoUnit.MINUTES))
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(redisTokenService.incrementOtpAttempts("verify-account:" + email, 300)).thenReturn(5);

        VerifyOtpRequest request = VerifyOtpRequest.builder()
                .email(email)
                .otp("999999")
                .build();

        AppException ex = assertThrows(AppException.class, () -> authService.verifyOtp(request));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("quá 5 lần"));

        // Xác nhận OTP trong DB đã bị xóa về null
        assertNull(user.getOtp());
        verify(userRepository, times(1)).save(user);
        verify(redisTokenService, times(1)).deleteOtp("verify-account:" + email);
    }

    @Test
    @DisplayName("6. Quên mật khẩu với email không tồn tại: Trả về thành công an toàn (chống Enumeration)")
    void forgotPassword_EmailNotExists_ReturnsSafelyWithoutError() {
        String email = "unknown@ecommerce.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setIdentity(email);

        assertDoesNotThrow(() -> authService.forgotPassword(request));
        verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("7. Quên mật khẩu Cách B: Xác thực OTP thành công cấp resetToken, sau đó đặt lại mật khẩu và thu hồi toàn bộ phiên")
    void resetPassword_CáchB_Success_RevokesAllSessions() {
        String email = "reset@ecommerce.com";
        String rawOtp = "654321";
        String hashedOtp = hashOtp(rawOtp);

        User user = User.builder()
                .id("u-reset")
                .email(email)
                .password("old_pass")
                .build();

        when(redisTokenService.getOtp("forgot-password:" + email)).thenReturn(hashedOtp);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new_pass123")).thenReturn("encoded_new_pass");

        // Bước 2a: Xác thực OTP lấy resetToken
        com.ecommerce.dto.request.VerifyResetOtpRequest verifyReq = new com.ecommerce.dto.request.VerifyResetOtpRequest(email, rawOtp);
        com.ecommerce.dto.response.VerifyResetOtpResponse verifyRes = authService.verifyResetOtp(verifyReq);

        assertNotNull(verifyRes);
        assertNotNull(verifyRes.getResetToken());
        assertEquals(600L, verifyRes.getExpiresIn());
        verify(redisTokenService, times(1)).savePasswordResetToken(eq("u-reset"), anyString(), eq(600L));

        // Bước 2b: Đặt lại mật khẩu bằng resetToken
        when(redisTokenService.consumePasswordResetTokenAtomic(anyString())).thenReturn("u-reset");
        when(userRepository.findById("u-reset")).thenReturn(Optional.of(user));

        ResetPasswordRequest resetReq = new ResetPasswordRequest();
        resetReq.setResetToken(verifyRes.getResetToken());
        resetReq.setNewPassword("new_pass123");

        authService.resetPassword(resetReq);

        verify(passwordEncoder, times(1)).encode("new_pass123");
        verify(userRepository, times(1)).save(user);
        verify(redisTokenService, times(1)).revokeAllSessions("u-reset");
        verify(emailService, times(1)).sendPasswordChangedEmail(email);
    }

    @Test
    @DisplayName("7b. Đặt lại mật khẩu thất bại khi resetToken sai, hết hạn hoặc đã tiêu thụ")
    void resetPassword_InvalidOrExpiredToken_ThrowsResetTokenInvalid() {
        when(redisTokenService.consumePasswordResetTokenAtomic(anyString())).thenReturn(null);

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setResetToken("invalid_or_expired_token");
        request.setNewPassword("new_pass123");

        AppException ex = assertThrows(AppException.class, () -> authService.resetPassword(request));
        assertEquals(ErrorCode.RESET_TOKEN_INVALID, ex.getErrorCode());
        verify(userRepository, never()).save(any());
        verify(redisTokenService, never()).revokeAllSessions(any());
    }

    @Test
    @DisplayName("7c. verify-reset-otp khi OTP sai: ném lỗi, không sinh và không trả resetToken")
    void verifyResetOtp_WrongOtp_ThrowsException_NoResetToken() {
        String email = "victim@ecommerce.com";
        String correctHashedOtp = hashOtp("123456");
        User user = User.builder().id("u-victim").email(email).build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(redisTokenService.getOtp("forgot-password:" + email)).thenReturn(correctHashedOtp);
        when(redisTokenService.incrementOtpAttempts("forgot-password:" + email, 300)).thenReturn(1);

        com.ecommerce.dto.request.VerifyResetOtpRequest request =
                new com.ecommerce.dto.request.VerifyResetOtpRequest(email, "999999");

        AppException ex = assertThrows(AppException.class, () -> authService.verifyResetOtp(request));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("không chính xác"));

        verify(redisTokenService, never()).savePasswordResetToken(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("7d. verify-reset-otp khi email không tồn tại: trả cùng lỗi như OTP sai, không sinh resetToken")
    void verifyResetOtp_NonExistentEmail_ThrowsSameErrorAsWrongOtp() {
        String email = "ghost@ecommerce.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(redisTokenService.incrementOtpAttempts("forgot-password:" + email, 300)).thenReturn(1);

        com.ecommerce.dto.request.VerifyResetOtpRequest request =
                new com.ecommerce.dto.request.VerifyResetOtpRequest(email, "any_otp");

        AppException ex = assertThrows(AppException.class, () -> authService.verifyResetOtp(request));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("không chính xác"));

        verify(redisTokenService, never()).savePasswordResetToken(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("7e. verifyOtp với user đã active và OTP sai: ném lỗi, tuyệt đối không trả true")
    void verifyOtp_ActiveUserWithWrongOtp_ThrowsException_DoesNotReturnTrue() {
        String email = "activeuser@ecommerce.com";
        User activeUser = User.builder()
                .id("u-active")
                .email(email)
                .active(true)
                .emailVerified(true)
                .build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(activeUser));
        when(redisTokenService.incrementOtpAttempts("verify-account:" + email, 300)).thenReturn(1);

        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail(email);
        request.setOtp("wrong_random_otp");

        AppException ex = assertThrows(AppException.class, () -> authService.verifyOtp(request));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("không chính xác"));
    }

    @Test
    @DisplayName("7f. reset-password không có resetToken (null hoặc rỗng): ném RESET_TOKEN_INVALID")
    void resetPassword_BlankResetToken_ThrowsResetTokenInvalid() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setResetToken("   ");
        request.setNewPassword("new_password123");

        AppException ex = assertThrows(AppException.class, () -> authService.resetPassword(request));
        assertEquals(ErrorCode.RESET_TOKEN_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("7g. reset-password với token của User A chỉ đổi mật khẩu cho User A, không thể tác động User B")
    void resetPassword_UserACannotResetUserB_TokenBoundToOriginalUser() {
        String rawToken = "token_of_user_A";
        String tokenHash = hashOtp(rawToken);

        User userA = User.builder().id("user-A").email("usera@ecommerce.com").password("old_pass_A").build();
        User userB = User.builder().id("user-B").email("userb@ecommerce.com").password("old_pass_B").build();

        when(redisTokenService.consumePasswordResetTokenAtomic(tokenHash)).thenReturn("user-A");
        when(userRepository.findById("user-A")).thenReturn(Optional.of(userA));
        when(passwordEncoder.encode("new_pass_for_A")).thenReturn("encoded_pass_A");

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setResetToken(rawToken);
        request.setNewPassword("new_pass_for_A");

        authService.resetPassword(request);

        verify(userRepository, times(1)).save(userA);
        verify(userRepository, never()).save(userB);
        verify(redisTokenService, times(1)).revokeAllSessions("user-A");
        verify(redisTokenService, never()).revokeAllSessions("user-B");
    }

    @Test
    @DisplayName("8. Logout: Thu hồi toàn bộ refresh token của user và blacklist access token (chấp nhận token hết hạn)")
    void logout_RevokesAllRefreshTokensAndBlacklistsAccessToken() {
        String token = "access_token_sample";
        Claims claims = mock(Claims.class);
        when(claims.get(JwtUtil.CLAIM_USER_ID, String.class)).thenReturn("u-123");
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 60000));
        when(jwtUtil.parseAccessTokenAllowExpired(token)).thenReturn(claims);

        authService.logout(token);

        verify(redisTokenService, times(1)).deleteAllRefreshTokensForUser("u-123");
        verify(redisTokenService, times(1)).blacklistAccessToken(eq(token), anyLong());
    }

    @Test
    @DisplayName("8b. Logout có chỉ định refreshToken: Chỉ thu hồi đúng refresh token đó")
    void logout_WithRefreshToken_RevokesOnlySpecificRefreshToken() {
        String accessToken = "access_token_sample";
        String refreshToken = "refresh_token_sample";

        Claims accessClaims = mock(Claims.class);
        when(accessClaims.get(JwtUtil.CLAIM_USER_ID, String.class)).thenReturn("u-123");
        when(accessClaims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 60000));
        when(jwtUtil.parseAccessTokenAllowExpired(accessToken)).thenReturn(accessClaims);

        Claims refreshClaims = mock(Claims.class);
        when(refreshClaims.get(JwtUtil.CLAIM_USER_ID, String.class)).thenReturn("u-123");
        when(refreshClaims.getId()).thenReturn("jti-device-1");
        when(jwtUtil.verifyRefreshToken(refreshToken)).thenReturn(refreshClaims);

        com.ecommerce.dto.request.LogoutRequest req = new com.ecommerce.dto.request.LogoutRequest(refreshToken);
        authService.logout(accessToken, req);

        verify(redisTokenService, times(1)).deleteRefreshToken("u-123", "jti-device-1");
        verify(redisTokenService, never()).deleteAllRefreshTokensForUser(anyString());
    }

    @Test
    @DisplayName("8c. LogoutAll: Thu hồi toàn bộ phiên làm việc của user")
    void logoutAll_CallsRevokeAllSessions() {
        authService.logoutAll("u-123");
        verify(redisTokenService, times(1)).revokeAllSessions("u-123");
    }

    @Test
    @DisplayName("9. Đăng nhập thành công khi isActive=true: Trả về tokens")
    void login_ActiveUser_Success() {
        String email = "verified@gmail.com";
        User activeUser = User.builder()
                .id("u3")
                .email(email)
                .password("encoded_pass")
                .active(true)
                .roles(Set.of(RoleEnum.ROLE_CUSTOMER))
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("password123", "encoded_pass")).thenReturn(true);
        when(jwtUtil.generateAccessToken(any())).thenReturn("mock_access_token");
        when(jwtUtil.generateRefreshToken(any(), anyString())).thenReturn("mock_refresh_token");

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword("password123");

        TokenResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("mock_access_token", response.getAccessToken());
        assertEquals("mock_refresh_token", response.getRefreshToken());
    }

    @Test
    @DisplayName("10. Gửi lại OTP thành công cho tài khoản chưa kích hoạt")
    void resendVerificationOtp_Success() {
        String email = "resend@gmail.com";
        User user = User.builder()
                .id("u4")
                .email(email)
                .active(false)
                .emailVerified(false)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        com.ecommerce.dto.request.ResendOtpRequest req = new com.ecommerce.dto.request.ResendOtpRequest(email);
        authService.resendVerificationOtp(req);

        verify(otpThrottleService, times(1)).checkAndRecordOtpRequest(email, "verify-account");
        verify(emailService, times(1)).sendOtpEmail(eq(email), anyString());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    @DisplayName("11. Gửi lại OTP bị bỏ qua an toàn khi tài khoản đã verify hoặc không tồn tại")
    void resendVerificationOtp_IgnoredWhenVerifiedOrNotFound() {
        String email = "verified@gmail.com";
        User user = User.builder()
                .id("u5")
                .email(email)
                .active(true)
                .emailVerified(true)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        com.ecommerce.dto.request.ResendOtpRequest req = new com.ecommerce.dto.request.ResendOtpRequest(email);
        assertDoesNotThrow(() -> authService.resendVerificationOtp(req));

        verify(otpThrottleService, times(1)).checkAndRecordOtpRequest(email, "verify-account");
        verify(emailService, never()).sendOtpEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("12. Đăng ký lại khi tài khoản chưa kích hoạt: Cập nhật thông tin và cấp lại OTP mới")
    void register_UnverifiedUser_UpdatesAndResendsOtp() {
        String email = "unverified2@gmail.com";
        User existingUser = User.builder()
                .id("u6")
                .email(email)
                .fullName("Old Name")
                .active(false)
                .emailVerified(false)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.encode("new_password123")).thenReturn("encoded_new");

        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setFullName("New Name");
        request.setPassword("new_password123");

        authService.register(request);

        assertEquals("New Name", existingUser.getFullName());
        assertEquals("encoded_new", existingUser.getPassword());
        verify(userRepository, times(1)).save(existingUser);
        verify(emailService, times(1)).sendOtpEmail(eq(email), anyString());
    }

    @Test
    @DisplayName("13. Đăng ký khi email đã verify: Ném EMAIL_ALREADY_EXISTS")
    void register_VerifiedUser_ThrowsEmailAlreadyExists() {
        String email = "verified2@gmail.com";
        User existingUser = User.builder()
                .id("u7")
                .email(email)
                .active(true)
                .emailVerified(true)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));

        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setFullName("Test");
        request.setPassword("password123");

        AppException ex = assertThrows(AppException.class, () -> authService.register(request));
        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("14. Đăng ký đồng thời bị va chạm DuplicateKeyException: Bắt và chuyển thành EMAIL_ALREADY_EXISTS")
    void register_ConcurrentDuplicateKey_ThrowsEmailAlreadyExists() {
        String email = "concurrent@gmail.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(User.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("Duplicate email key"));

        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setFullName("Test Concurrent");
        request.setPassword("password123");

        AppException ex = assertThrows(AppException.class, () -> authService.register(request));
        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("15. Xác thực OTP khi vượt quá 10 lần sai trong 1 giờ: Ném OTP_MAX_ATTEMPTS_EXCEEDED")
    void verifyOtp_ExceedsHourlyFailedAttempts_ThrowsOtpMaxAttemptsExceeded() {
        String email = "blocked_hourly@gmail.com";
        when(redisTokenService.getHourlyFailedAttempts("verify-account", email)).thenReturn(10);

        VerifyOtpRequest request = VerifyOtpRequest.builder()
                .email(email)
                .otp("123456")
                .build();

        AppException ex = assertThrows(AppException.class, () -> authService.verifyOtp(request));
        assertEquals(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED, ex.getErrorCode());
    }

    @Test
    @DisplayName("16. Xác thực OTP với user không tồn tại hoặc đã active: Trả cùng lỗi như sai OTP, không lộ trạng thái")
    void verifyOtp_NonExistentUser_ReturnsSameErrorAndIncrementsAttempts() {
        String email = "nonexistent@gmail.com";
        when(redisTokenService.getHourlyFailedAttempts("verify-account", email)).thenReturn(0);
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(redisTokenService.incrementOtpAttempts("verify-account:" + email, 300)).thenReturn(1);

        VerifyOtpRequest request = VerifyOtpRequest.builder()
                .email(email)
                .otp("123456")
                .build();

        AppException ex = assertThrows(AppException.class, () -> authService.verifyOtp(request));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("1/5"));
        verify(redisTokenService, times(1)).incrementHourlyFailedAttempts("verify-account", email, 3600);
    }

    @Test
    @DisplayName("17. Phát hiện tái sử dụng Refresh Token: Thu hồi toàn bộ phiên đăng nhập (theft protection)")
    void refreshToken_ReusedTokenDetected_RevokesAllSessions() {
        String refreshToken = "stolen_refresh_token";
        Claims claims = mock(Claims.class);
        when(claims.get(JwtUtil.CLAIM_USER_ID, String.class)).thenReturn("u-victim");
        when(claims.getId()).thenReturn("jti-stolen");
        when(jwtUtil.verifyRefreshToken(refreshToken)).thenReturn(claims);

        // Đã có dấu đã dùng
        when(redisTokenService.isRefreshTokenUsed("u-victim", "jti-stolen")).thenReturn(true);

        RefreshTokenRequest req = new RefreshTokenRequest(refreshToken);
        AppException ex = assertThrows(AppException.class, () -> authService.refreshToken(req));
        assertEquals(ErrorCode.TOKEN_REVOKED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("tái sử dụng"));

        verify(redisTokenService, times(1)).revokeAllSessions("u-victim");
        verify(redisTokenService, never()).deleteRefreshTokenAtomic(anyString(), anyString());
    }

    @Test
    @DisplayName("18. Cấp mới token bằng Refresh Token với thu hồi atomic thành công")
    void refreshToken_AtomicDelete_Success() {
        String refreshToken = "valid_refresh_token";
        Claims claims = mock(Claims.class);
        when(claims.get(JwtUtil.CLAIM_USER_ID, String.class)).thenReturn("u-valid");
        when(claims.getId()).thenReturn("jti-valid");
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 604800000L));
        when(jwtUtil.verifyRefreshToken(refreshToken)).thenReturn(claims);

        when(redisTokenService.isRefreshTokenUsed("u-valid", "jti-valid")).thenReturn(false);
        when(redisTokenService.deleteRefreshTokenAtomic("u-valid", "jti-valid")).thenReturn(true);

        User user = User.builder()
                .id("u-valid")
                .email("valid@ecommerce.com")
                .active(true)
                .emailVerified(true)
                .roles(Set.of(RoleEnum.ROLE_CUSTOMER))
                .build();
        when(userRepository.findById("u-valid")).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(any())).thenReturn("new_access");
        when(jwtUtil.generateRefreshToken(any(), anyString())).thenReturn("new_refresh");

        RefreshTokenRequest req = new RefreshTokenRequest(refreshToken);
        TokenResponse res = authService.refreshToken(req);

        assertNotNull(res);
        assertEquals("new_access", res.getAccessToken());
        assertEquals("new_refresh", res.getRefreshToken());
        verify(redisTokenService, times(1)).markRefreshTokenAsUsed(eq("u-valid"), eq("jti-valid"), anyLong());
    }

    @Test
    @DisplayName("19. Đăng nhập sai mật khẩu: Ghi nhận login failure và ném BAD_CREDENTIALS")
    void login_WrongPassword_ThrowsBadCredentialsAndRecordsFailure() {
        String email = "wrongpass@ecommerce.com";
        User user = User.builder()
                .id("u-wrong")
                .email(email)
                .password("encoded_pass")
                .active(true)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong_secret", "encoded_pass")).thenReturn(false);

        LoginRequest req = new LoginRequest(email, "wrong_secret");
        AppException ex = assertThrows(AppException.class, () -> authService.login(req, "192.168.1.5"));
        assertEquals(ErrorCode.BAD_CREDENTIALS, ex.getErrorCode());

        verify(redisTokenService, times(1)).recordLoginFailure(email, "192.168.1.5", 900L);
    }

    @Test
    @DisplayName("20. Đăng nhập với email không tồn tại: Thực thi dummy matches và ném BAD_CREDENTIALS")
    void login_NonExistentEmail_PerformsDummyMatchesAndThrowsBadCredentials() {
        String email = "ghost@ecommerce.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        LoginRequest req = new LoginRequest(email, "any_secret");
        AppException ex = assertThrows(AppException.class, () -> authService.login(req, "192.168.1.6"));
        assertEquals(ErrorCode.BAD_CREDENTIALS, ex.getErrorCode());

        verify(passwordEncoder, times(1)).matches(eq("any_secret"), anyString());
        verify(redisTokenService, times(1)).recordLoginFailure(email, "192.168.1.6", 900L);
    }

    @Test
    @DisplayName("21. Khóa tạm đăng nhập khi sai quá 5 lần từ một IP: Ném ACCOUNT_TEMP_LOCKED (429)")
    void login_ExceedsIpLockout_ThrowsAccountTempLocked() {
        String email = "victim@ecommerce.com";
        when(redisTokenService.getLoginFailAttemptsByIp(email, "10.0.0.1")).thenReturn(5);

        LoginRequest req = new LoginRequest(email, "any_pass");
        AppException ex = assertThrows(AppException.class, () -> authService.login(req, "10.0.0.1"));
        assertEquals(ErrorCode.ACCOUNT_TEMP_LOCKED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("15 phút"));
    }

    @Test
    @DisplayName("22. Khóa tạm đăng nhập khi email bị thử sai quá 20 lần: Ném ACCOUNT_TEMP_LOCKED (429)")
    void login_ExceedsEmailLockout_ThrowsAccountTempLocked() {
        String email = "targeted@ecommerce.com";
        when(redisTokenService.getLoginFailAttemptsByIp(email, "10.0.0.2")).thenReturn(2);
        when(redisTokenService.getLoginFailAttemptsByEmail(email)).thenReturn(20);

        LoginRequest req = new LoginRequest(email, "any_pass");
        AppException ex = assertThrows(AppException.class, () -> authService.login(req, "10.0.0.2"));
        assertEquals(ErrorCode.ACCOUNT_TEMP_LOCKED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("15 phút"));
    }
}

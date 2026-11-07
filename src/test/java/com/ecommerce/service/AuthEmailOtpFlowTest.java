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

    private AppProperties appProperties;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getJwt().setAccessExpirationMs(3600000L);
        appProperties.getJwt().setRefreshExpirationMs(604800000L);

        authService = new AuthServiceImpl(
                userRepository,
                passwordEncoder,
                authenticationManager,
                jwtUtil,
                redisTokenService,
                emailService,
                appProperties,
                environment
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

        when(userRepository.existsByEmail("nguyenvana@gmail.com")).thenReturn(false);
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
    @DisplayName("7. Đặt lại mật khẩu thành công: Thu hồi toàn bộ refresh token của user")
    void resetPassword_Success_RevokesAllRefreshTokens() {
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

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setIdentity(email);
        request.setOtp(rawOtp);
        request.setNewPassword("new_pass123");

        authService.resetPassword(request);

        verify(passwordEncoder, times(1)).encode("new_pass123");
        verify(userRepository, times(1)).save(user);
        verify(redisTokenService, times(1)).deleteAllRefreshTokensForUser("u-reset");
    }

    @Test
    @DisplayName("8. Logout: Thu hồi toàn bộ refresh token của user và blacklist access token")
    void logout_RevokesAllRefreshTokensAndBlacklistsAccessToken() {
        String token = "access_token_sample";
        Claims claims = mock(Claims.class);
        when(claims.get(JwtUtil.CLAIM_USER_ID, String.class)).thenReturn("u-123");
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 60000));
        when(jwtUtil.verifyAccessToken(token)).thenReturn(claims);

        authService.logout(token);

        verify(redisTokenService, times(1)).deleteAllRefreshTokensForUser("u-123");
        verify(redisTokenService, times(1)).blacklistAccessToken(eq(token), anyLong());
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

        CustomUserDetails userDetails = new CustomUserDetails(
                "u3", email, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
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
}

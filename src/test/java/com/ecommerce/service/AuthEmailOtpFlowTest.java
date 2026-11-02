package com.ecommerce.service;

import com.ecommerce.common.constant.RoleEnum;
import com.ecommerce.config.AppProperties;
import com.ecommerce.document.User;
import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.request.RegisterRequest;
import com.ecommerce.dto.request.VerifyOtpRequest;
import com.ecommerce.dto.response.TokenResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.security.CustomUserDetails;
import com.ecommerce.service.impl.AuthServiceImpl;
import com.ecommerce.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
                appProperties
        );
    }

    @Test
    @DisplayName("1. Đăng ký tài khoản: Lưu user với isActive=false, sinh OTP 6 số và gửi email")
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
        assertNotNull(savedUser.getOtp(), "Mã OTP phải được sinh ra");
        assertEquals(6, savedUser.getOtp().length(), "Mã OTP phải gồm 6 chữ số");
        assertNotNull(savedUser.getOtpExpiryTime());
        assertTrue(savedUser.getOtpExpiryTime().isAfter(Instant.now()), "OTP phải có thời hạn trong tương lai (5 phút)");

        // Kiểm tra đã gọi gửi email qua EmailService
        verify(emailService, times(1)).sendOtpEmail(eq("nguyenvana@gmail.com"), eq(savedUser.getOtp()));
    }

    @Test
    @DisplayName("2. Chặn đăng nhập: Tài khoản isActive=false bị chặn với thông báo chính xác")
    void login_InactiveUser_ThrowsException() {
        String email = "unverified@gmail.com";
        User inactiveUser = User.builder()
                .id("u1")
                .email(email)
                .password("encoded_pass")
                .active(false) // Chưa kích hoạt
                .roles(Set.of(RoleEnum.ROLE_CUSTOMER))
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(inactiveUser));

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword("password123");

        AppException ex = assertThrows(AppException.class, () -> authService.login(loginRequest));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        assertEquals("Tài khoản chưa được kích hoạt, vui lòng kiểm tra email", ex.getMessage());
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("3. Xác thực OTP: Mã đúng và còn hạn -> Cập nhật isActive=true")
    void verifyOtp_Success_ActivatesUser() {
        String email = "verify@gmail.com";
        String otp = "123456";
        User user = User.builder()
                .id("u2")
                .email(email)
                .active(false)
                .otp(otp)
                .otpExpiryTime(Instant.now().plus(4, ChronoUnit.MINUTES))
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        VerifyOtpRequest request = VerifyOtpRequest.builder()
                .email(email)
                .otp(otp)
                .build();

        boolean result = authService.verifyOtp(request);

        assertTrue(result);
        assertTrue(user.isActive(), "Tài khoản phải được chuyển sang isActive = true");
        assertTrue(user.isEmailVerified());
        assertNull(user.getOtp(), "Mã OTP phải bị xóa sau khi kích hoạt thành công");
        assertNull(user.getOtpExpiryTime());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    @DisplayName("4. Xác thực OTP thất bại: Mã OTP sai hoặc hết hạn -> ném ngoại lệ VALIDATION_ERROR")
    void verifyOtp_InvalidOtp_ThrowsException() {
        String email = "verify@gmail.com";
        User user = User.builder()
                .id("u2")
                .email(email)
                .active(false)
                .otp("654321")
                .otpExpiryTime(Instant.now().plus(4, ChronoUnit.MINUTES))
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        VerifyOtpRequest request = VerifyOtpRequest.builder()
                .email(email)
                .otp("000000") // Sai OTP
                .build();

        AppException ex = assertThrows(AppException.class, () -> authService.verifyOtp(request));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertFalse(user.isActive());
        verify(userRepository, never()).save(user);
    }

    @Test
    @DisplayName("5. Đăng nhập thành công khi isActive=true: Trả về tokens")
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

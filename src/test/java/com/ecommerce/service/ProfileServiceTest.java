package com.ecommerce.service;

import com.ecommerce.document.User;
import com.ecommerce.dto.request.ChangePasswordRequest;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.impl.ProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RedisTokenService redisTokenService;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordPolicyService passwordPolicyService;

    @InjectMocks
    private ProfileServiceImpl profileService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id("user-123")
                .email("test@ecommerce.com")
                .password("encoded_old_password")
                .build();
    }

    @Test
    @DisplayName("N9: changePassword thành công - Hash mật khẩu mới, lưu DB, thu hồi mọi phiên và gửi email thông báo")
    void changePassword_Success_HashesPasswordRevokesSessionsSendsEmail() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("OldPassword123@");
        request.setNewPassword("NewStrongPass123@");

        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPassword123@", "encoded_old_password")).thenReturn(true);
        when(passwordEncoder.encode("NewStrongPass123@")).thenReturn("encoded_new_password");

        assertDoesNotThrow(() -> profileService.changePassword("user-123", request));

        assertEquals("encoded_new_password", user.getPassword());
        verify(userRepository, times(1)).save(user);
        verify(redisTokenService, times(1)).revokeAllSessions("user-123");
        verify(emailService, times(1)).sendPasswordChangedEmail("test@ecommerce.com");
    }

    @Test
    @DisplayName("N3.c: changePassword khi thu hồi phiên thất bại - Mật khẩu đã lưu trong DB và ném lỗi rõ ràng")
    void changePassword_RevokeSessionsFails_ThrowsExceptionAfterPasswordSaved() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("OldPassword123@");
        request.setNewPassword("NewStrongPass123@");

        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPassword123@", "encoded_old_password")).thenReturn(true);
        when(passwordEncoder.encode("NewStrongPass123@")).thenReturn("encoded_new_password");
        doThrow(new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE, "Redis connection failed"))
                .when(redisTokenService).revokeAllSessions("user-123");

        AppException ex = assertThrows(AppException.class, () -> profileService.changePassword("user-123", request));
        assertEquals(ErrorCode.AUTH_SERVICE_UNAVAILABLE, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("chưa đăng xuất được các thiết bị khác"));

        // Xác nhận mật khẩu đã được lưu trước khi ném lỗi
        assertEquals("encoded_new_password", user.getPassword());
        verify(userRepository, times(1)).save(user);
    }
}

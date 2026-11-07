package com.ecommerce.service;

import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.service.impl.PasswordPolicyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordPolicyServiceTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordPolicyServiceImpl passwordPolicyService;

    @BeforeEach
    void setUp() {
        passwordPolicyService.initCommonPasswords();
    }

    @Test
    @DisplayName("Mật khẩu hợp lệ: 8-64 ký tự, không trùng email, không nằm trong danh sách phổ biến")
    void validatePassword_Valid_Success() {
        assertDoesNotThrow(() ->
                passwordPolicyService.validatePassword("ComplexPass@2026", "user@ecommerce.com"));
    }

    @Test
    @DisplayName("Mật khẩu quá ngắn (< 8 ký tự): Ném VALIDATION_ERROR")
    void validatePassword_TooShort_ThrowsException() {
        AppException ex = assertThrows(AppException.class, () ->
                passwordPolicyService.validatePassword("1234567", "user@ecommerce.com"));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("8 đến 64"));
    }

    @Test
    @DisplayName("Mật khẩu trùng email: Ném VALIDATION_ERROR")
    void validatePassword_MatchesEmail_ThrowsException() {
        AppException ex = assertThrows(AppException.class, () ->
                passwordPolicyService.validatePassword("User@Ecommerce.Com", "user@ecommerce.com"));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("không được trùng với địa chỉ email"));
    }

    @Test
    @DisplayName("Mật khẩu nằm trong danh sách phổ biến (ví dụ 12345678): Ném VALIDATION_ERROR")
    void validatePassword_CommonPassword_ThrowsException() {
        AppException ex = assertThrows(AppException.class, () ->
                passwordPolicyService.validatePassword("12345678", "user@ecommerce.com"));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("quá đơn giản"));
    }

    @Test
    @DisplayName("Đổi mật khẩu: Mật khẩu mới trùng mật khẩu cũ -> Ném VALIDATION_ERROR")
    void validateNewPasswordDiffersFromOld_SamePassword_ThrowsException() {
        when(passwordEncoder.matches("MySecurePass123", "encodedOldPass")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () ->
                passwordPolicyService.validateNewPasswordDiffersFromOld("MySecurePass123", "encodedOldPass"));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("phải khác với mật khẩu hiện tại"));
    }

    @Test
    @DisplayName("Validate Phone: Số điện thoại Việt Nam hợp lệ (098... hoặc +849...)")
    void validatePhone_Valid() {
        assertDoesNotThrow(() -> passwordPolicyService.validatePhone("0987654321"));
        assertDoesNotThrow(() -> passwordPolicyService.validatePhone("+84912345678"));
        assertDoesNotThrow(() -> passwordPolicyService.validatePhone(null));
        assertDoesNotThrow(() -> passwordPolicyService.validatePhone(""));
    }

    @Test
    @DisplayName("Validate Phone: Số điện thoại không hợp lệ -> Ném VALIDATION_ERROR")
    void validatePhone_Invalid_ThrowsException() {
        AppException ex = assertThrows(AppException.class, () ->
                passwordPolicyService.validatePhone("0123456789")); // Đầu số 012 cũ không hợp lệ
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Số điện thoại không đúng định dạng"));
    }
}

package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.dto.request.VerifyResetOtpRequest;
import com.ecommerce.dto.response.VerifyResetOtpResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.exception.GlobalExceptionHandler;
import com.ecommerce.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("reset-password theo body cũ (identity + otp + newPassword): bị từ chối với HTTP 400 vì thiếu resetToken")
    void resetPassword_OldBodyWithIdentityAndOtp_RejectedWithHttp400() throws Exception {
        String oldBodyJson = """
                {
                    "identity": "customer@ecommerce.com",
                    "otp": "123456",
                    "newPassword": "Password123@"
                }
                """;

        mockMvc.perform(post(ApiRoutes.Auth.BASE + ApiRoutes.Auth.RESET_PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oldBodyJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verify(authService, never()).resetPassword(any());
    }

    @Test
    @DisplayName("verify-reset-otp khi OTP sai: trả lỗi, response KHÔNG có resetToken")
    void verifyResetOtp_WrongOtp_ReturnsErrorWithoutResetToken() throws Exception {
        when(authService.verifyResetOtp(any(VerifyResetOtpRequest.class)))
                .thenThrow(new AppException(ErrorCode.VALIDATION_ERROR, "Mã OTP xác thực không chính xác hoặc đã hết hạn"));

        String requestJson = """
                {
                    "identity": "customer@ecommerce.com",
                    "otp": "999999"
                }
                """;

        mockMvc.perform(post(ApiRoutes.Auth.BASE + ApiRoutes.Auth.VERIFY_RESET_OTP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.resetToken").doesNotExist());
    }

    @Test
    @DisplayName("verify-reset-otp khi đúng OTP: thành công và trả về resetToken")
    void verifyResetOtp_CorrectOtp_ReturnsResetToken() throws Exception {
        VerifyResetOtpResponse response = VerifyResetOtpResponse.builder()
                .resetToken("SECURE_RANDOM_TOKEN_32_BYTES")
                .expiresIn(600L)
                .build();

        when(authService.verifyResetOtp(any(VerifyResetOtpRequest.class))).thenReturn(response);

        String requestJson = """
                {
                    "identity": "customer@ecommerce.com",
                    "otp": "123456"
                }
                """;

        mockMvc.perform(post(ApiRoutes.Auth.BASE + ApiRoutes.Auth.VERIFY_RESET_OTP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.resetToken").value("SECURE_RANDOM_TOKEN_32_BYTES"))
                .andExpect(jsonPath("$.data.expiresIn").value(600));
    }
}

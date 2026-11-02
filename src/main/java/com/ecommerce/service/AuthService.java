package com.ecommerce.service;

import com.ecommerce.dto.request.*;
import com.ecommerce.dto.response.TokenResponse;

public interface AuthService {

    /**
     * Đăng ký tài khoản:
     * - Lưu thông tin User với isActive = false
     * - Sinh mã OTP 6 số ngẫu nhiên thời hạn 5 phút
     * - Gửi email chứa mã OTP qua JavaMailSender
     */
    void register(RegisterRequest request);

    /**
     * Xác thực email bằng OTP:
     * - Kiểm tra mã OTP và thời hạn 5 phút
     * - Nếu hợp lệ, cập nhật isActive = true, emailVerified = true
     */
    boolean verifyOtp(VerifyOtpRequest request);

    /**
     * Đăng nhập:
     * - Chặn nếu isActive == false (ném ngoại lệ "Tài khoản chưa được kích hoạt, vui lòng kiểm tra email")
     * - Nếu hợp lệ, cấp Access Token & Refresh Token
     */
    TokenResponse login(LoginRequest request);

    TokenResponse refreshToken(RefreshTokenRequest request);

    void logout(String accessToken);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    void sendVerificationOtp(String userId, SendOtpRequest request);

    void verifyAccount(String userId, VerifyAccountRequest request);
}

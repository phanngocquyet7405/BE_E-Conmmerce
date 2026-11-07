package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.dto.request.*;
import com.ecommerce.dto.response.TokenResponse;
import com.ecommerce.dto.response.VerifyResetOtpResponse;
import com.ecommerce.security.CustomUserDetails;
import com.ecommerce.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiRoutes.Auth.BASE)
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 1. Đăng ký tài khoản (Register)
     * Hệ thống lưu User với isActive = false, sinh mã OTP 6 số và gửi qua email.
     */
    @PostMapping(ApiRoutes.Auth.REGISTER)
    public ResponseEntity<ApiResponse<String>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đăng ký tài khoản thành công! Mã OTP xác thực đã được gửi tới email (có hiệu lực trong 5 phút).", null));
    }

    /**
     * Gửi lại mã OTP xác thực tài khoản (Public - dành cho tài khoản chưa kích hoạt)
     */
    @PostMapping(ApiRoutes.Auth.RESEND_VERIFICATION_OTP)
    public ResponseEntity<ApiResponse<Void>> resendVerificationOtp(@Valid @RequestBody ResendOtpRequest request) {
        authService.resendVerificationOtp(request);
        return ResponseEntity.ok(ApiResponse.ok("Nếu tài khoản tồn tại và chưa được kích hoạt, mã OTP xác thực đã được gửi tới email.", null));
    }

    /**
     * 2. Xác thực tài khoản bằng OTP (Verify OTP)
     * Nhận email và mã OTP. Nếu hợp lệ -> kích hoạt tài khoản isActive = true.
     */
    @PostMapping(ApiRoutes.Auth.VERIFY_OTP)
    public ResponseEntity<ApiResponse<Boolean>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        boolean valid = authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.ok("Kích hoạt tài khoản thành công! Bạn có thể tiến hành đăng nhập.", valid));
    }

    /**
     * 3. Đăng nhập (Login)
     * Chặn nếu isActive == false, thông báo "Tài khoản chưa được kích hoạt, vui lòng kiểm tra email".
     */
    @PostMapping(ApiRoutes.Auth.LOGIN)
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "unknown";
        TokenResponse response = authService.login(request, clientIp);
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công", response));
    }

    /**
     * 4. Cấp lại Access Token mới
     */
    @PostMapping(ApiRoutes.Auth.REFRESH)
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cấp lại Access Token thành công", authService.refreshToken(request)));
    }

    /**
     * 5. Đăng xuất (Logout)
     * - Chấp nhận access token đã hết hạn
     * - Nếu gửi kèm refreshToken: chỉ thu hồi đúng refresh token đó
     * - Nếu không gửi kèm refreshToken: giữ hành vi cũ thu hồi tất cả (deprecated)
     */
    @PostMapping(ApiRoutes.Auth.LOGOUT)
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            @RequestBody(required = false) LogoutRequest body) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String accessToken = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7)
                : null;

        authService.logout(accessToken, body);
        return ResponseEntity.ok(ApiResponse.ok("Đăng xuất thành công", null));
    }

    /**
     * 5b. Đăng xuất toàn bộ thiết bị (Logout All Sessions)
     */
    @PostMapping(ApiRoutes.Auth.LOGOUT_ALL)
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new com.ecommerce.exception.AppException(com.ecommerce.exception.ErrorCode.UNAUTHENTICATED);
        }
        authService.logoutAll(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.ok("Đã đăng xuất toàn bộ thiết bị thành công", null));
    }

    /**
     * 6. Quên mật khẩu - Gửi mã OTP xác nhận về Email
     */
    @PostMapping(ApiRoutes.Auth.FORGOT_PASSWORD)
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Nếu email tồn tại trên hệ thống, mã OTP xác thực đã được gửi tới email của bạn.", null));
    }

    /**
     * 6b. Xác thực OTP quên mật khẩu - Bước 2a: Kiểm tra OTP và cấp resetToken dùng 1 lần (Cách B)
     */
    @PostMapping(ApiRoutes.Auth.VERIFY_RESET_OTP)
    public ResponseEntity<ApiResponse<VerifyResetOtpResponse>> verifyResetOtp(@Valid @RequestBody VerifyResetOtpRequest request) {
        VerifyResetOtpResponse response = authService.verifyResetOtp(request);
        return ResponseEntity.ok(ApiResponse.ok("Xác thực mã OTP thành công. Vui lòng thiết lập mật khẩu mới.", response));
    }

    /**
     * 7. Đặt lại mật khẩu mới bằng resetToken (Cách B)
     */
    @PostMapping(ApiRoutes.Auth.RESET_PASSWORD)
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Đặt lại mật khẩu thành công. Vui lòng đăng nhập với mật khẩu mới.", null));
    }

    /**
     * 8. Gửi lại mã OTP xác minh tài khoản (cho User đã đăng nhập)
     */
    @PostMapping(ApiRoutes.Auth.SEND_VERIFICATION_OTP)
    public ResponseEntity<ApiResponse<Void>> sendVerificationOtp(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody(required = false) SendOtpRequest request) {
        if (userDetails == null) {
            throw new com.ecommerce.exception.AppException(com.ecommerce.exception.ErrorCode.UNAUTHENTICATED);
        }
        SendOtpRequest req = request != null ? request : new SendOtpRequest();
        authService.sendVerificationOtp(userDetails.getId(), req);
        return ResponseEntity.ok(ApiResponse.ok("Đã gửi mã xác minh tài khoản", null));
    }

    /**
     * 9. Xác minh Email bằng mã OTP
     */
    @PostMapping(ApiRoutes.Auth.VERIFY_ACCOUNT)
    public ResponseEntity<ApiResponse<Void>> verifyAccount(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody VerifyAccountRequest request) {
        if (userDetails == null) {
            throw new com.ecommerce.exception.AppException(com.ecommerce.exception.ErrorCode.UNAUTHENTICATED);
        }
        authService.verifyAccount(userDetails.getId(), request);
        return ResponseEntity.ok(ApiResponse.ok("Xác minh tài khoản thành công", null));
    }
}

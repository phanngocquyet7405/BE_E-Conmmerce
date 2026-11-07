package com.ecommerce.service;

public interface EmailService {

    /**
     * Gửi email chứa mã OTP xác thực tài khoản dưới dạng template HTML
     *
     * @param toEmail Địa chỉ email người nhận
     * @param otp     Mã OTP 6 số
     */
    void sendOtpEmail(String toEmail, String otp);

    /**
     * Gửi email HTML tổng quát
     *
     * @param toEmail     Địa chỉ email người nhận
     * @param subject     Tiêu đề email
     * @param htmlContent Nội dung HTML
     */
    void sendHtmlEmail(String toEmail, String subject, String htmlContent);

    /**
     * Gửi email thông báo mật khẩu tài khoản vừa được thay đổi thành công
     *
     * @param toEmail Địa chỉ email người nhận
     */
    void sendPasswordChangedEmail(String toEmail);
}

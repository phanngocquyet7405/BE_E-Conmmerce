package com.ecommerce.service.impl;

import com.ecommerce.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String senderEmail;

    @Override
    public void sendOtpEmail(String toEmail, String otp) {
        String subject = "[Ecommerce] Mã OTP xác thực tài khoản";
        String htmlContent = buildOtpTemplate(otp);
        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    @Override
    public void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(senderEmail, "Ecommerce Store");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Đã gửi email thành công tới: {}", toEmail);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Lỗi khi gửi email tới {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Không thể gửi email xác thực. Vui lòng thử lại sau.", e);
        }
    }

    private String buildOtpTemplate(String otp) {
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Xác thực tài khoản</title>
                <style>
                    body {
                        font-family: 'Segoe UI', Arial, sans-serif;
                        background-color: #f3f4f6;
                        margin: 0;
                        padding: 24px;
                    }
                    .email-wrapper {
                        max-width: 540px;
                        margin: 0 auto;
                        background: #ffffff;
                        border-radius: 12px;
                        overflow: hidden;
                        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
                    }
                    .email-header {
                        background: linear-gradient(135deg, #2563eb, #1d4ed8);
                        color: #ffffff;
                        padding: 32px 24px;
                        text-align: center;
                    }
                    .email-header h1 {
                        margin: 0;
                        font-size: 22px;
                        font-weight: 700;
                        letter-spacing: 0.5px;
                    }
                    .email-body {
                        padding: 32px 28px;
                        color: #1f2937;
                        line-height: 1.6;
                    }
                    .greeting {
                        font-size: 16px;
                        margin-bottom: 16px;
                    }
                    .otp-box {
                        background-color: #eff6ff;
                        border: 2px dashed #3b82f6;
                        border-radius: 10px;
                        padding: 20px;
                        text-align: center;
                        margin: 28px 0;
                    }
                    .otp-code {
                        font-size: 36px;
                        font-weight: 800;
                        letter-spacing: 10px;
                        color: #1d4ed8;
                        font-family: 'Courier New', Courier, monospace;
                    }
                    .expiry-note {
                        font-size: 14px;
                        color: #64748b;
                        margin-top: 10px;
                    }
                    .alert-warning {
                        background-color: #fffbeb;
                        border-left: 4px solid #f59e0b;
                        padding: 12px 16px;
                        font-size: 13px;
                        color: #92400e;
                        border-radius: 4px;
                        margin-top: 24px;
                    }
                    .email-footer {
                        text-align: center;
                        font-size: 12px;
                        color: #9ca3af;
                        padding: 20px 24px;
                        background-color: #f9fafb;
                        border-top: 1px solid #f3f4f6;
                    }
                </style>
            </head>
            <body>
                <div class="email-wrapper">
                    <div class="email-header">
                        <h1>XÁC THỰC TÀI KHOẢN CỦA BẠN</h1>
                    </div>
                    <div class="email-body">
                        <div class="greeting">Xin chào quý khách,</div>
                        <p>Cảm ơn bạn đã đăng ký tài khoản tại hệ thống của chúng tôi. Để hoàn tất đăng ký và kích hoạt tài khoản, vui lòng nhập mã OTP dưới đây:</p>
                        
                        <div class="otp-box">
                            <div class="otp-code">""" + otp + """
                            </div>
                            <div class="expiry-note">Mã OTP có thời hạn hiệu lực trong <strong>5 phút</strong>.</div>
                        </div>

                        <div class="alert-warning">
                            🔒 <strong>Lưu ý bảo mật:</strong> Không chia sẻ mã OTP này cho bất kỳ ai. Chúng tôi không bao giờ yêu cầu cung cấp OTP qua điện thoại hoặc tin nhắn.
                        </div>
                    </div>
                    <div class="email-footer">
                        <p>Đây là email tự động, vui lòng không trả lời thư này.</p>
                        <p>&copy; 2026 Ecommerce Platform. Tất cả các quyền được bảo lưu.</p>
                    </div>
                </div>
            </body>
            </html>
            """;
    }
}

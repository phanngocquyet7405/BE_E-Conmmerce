package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyOtpRequest {

    private String email;

    private String identity;

    @NotBlank(message = "Mã OTP không được để trống")
    @Size(min = 6, max = 6, message = "Mã OTP gồm đúng 6 chữ số")
    private String otp;

    /**
     * Lấy email từ request (hỗ trợ cả trường 'email' và 'identity')
     */
    public String getTargetEmail() {
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        return identity != null ? identity.trim() : null;
    }
}
